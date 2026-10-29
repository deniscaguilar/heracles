#!/usr/bin/env python3
"""Prova P4: cenários de fila contra um emulador de SQS (descartável).

Uso: run_scenarios.py <elasticmq|localstack>
Usa só a biblioteca padrão e o AWS CLI já instalado. Nenhum dado de saúde: as
mensagens levam apenas um identificador de tarefa.
"""
import json
import os
import subprocess
import sys
import time
import uuid

ENDPOINTS = {
    "elasticmq": "http://localhost:19324",
    "localstack": "http://localhost:14566",
}
ENV = {**os.environ, "AWS_ACCESS_KEY_ID": "test", "AWS_SECRET_ACCESS_KEY": "test",
       "AWS_DEFAULT_REGION": "sa-east-1"}


class Sqs:
    def __init__(self, endpoint):
        self.endpoint = endpoint

    def call(self, *args):
        cmd = ["aws", "--endpoint-url", self.endpoint, "sqs", *args, "--output", "json"]
        out = subprocess.run(cmd, env=ENV, capture_output=True, text=True)
        if out.returncode != 0:
            raise RuntimeError(out.stderr.strip())
        return json.loads(out.stdout) if out.stdout.strip() else {}

    def create(self, name, attrs=None):
        args = ["create-queue", "--queue-name", name]
        if attrs:
            args += ["--attributes", json.dumps(attrs)]
        return self.call(*args)["QueueUrl"]

    def send(self, url, body, **extra):
        args = ["send-message", "--queue-url", url, "--message-body", body]
        for k, v in extra.items():
            args += [f"--{k}", str(v)]
        return self.call(*args)

    def receive(self, url, wait=1):
        r = self.call("receive-message", "--queue-url", url, "--max-number-of-messages", "1",
                      "--wait-time-seconds", str(wait), "--attribute-names", "All")
        return (r.get("Messages") or [None])[0]

    def delete(self, url, handle):
        self.call("delete-message", "--queue-url", url, "--receipt-handle", handle)

    def change_visibility(self, url, handle, seconds):
        self.call("change-message-visibility", "--queue-url", url, "--receipt-handle", handle,
                  "--visibility-timeout", str(seconds))

    def depth(self, url):
        a = self.call("get-queue-attributes", "--queue-url", url, "--attribute-names", "All")["Attributes"]
        return (int(a["ApproximateNumberOfMessages"]), int(a["ApproximateNumberOfMessagesNotVisible"]))

    def arn(self, url):
        return self.call("get-queue-attributes", "--queue-url", url,
                         "--attribute-names", "QueueArn")["Attributes"]["QueueArn"]


results = []


def check(name, ok, detail=""):
    results.append((name, ok, detail))
    print(f"  [{'OK' if ok else 'FALHA'}] {name} {detail}")


def make_queues(sqs, tag):
    dlq = sqs.create(f"{tag}-dlq")
    redrive = json.dumps({"deadLetterTargetArn": sqs.arn(dlq), "maxReceiveCount": "3"})
    main = sqs.create(f"{tag}-main", {"VisibilityTimeout": "2", "RedrivePolicy": redrive})
    return main, dlq


def scenario_basic(sqs):
    print("C1: enviar, receber e confirmar")
    main, _ = make_queues(sqs, f"c1-{uuid.uuid4().hex[:6]}")
    task = str(uuid.uuid4())
    sqs.send(main, json.dumps({"task_id": task}))
    msg = sqs.receive(main)
    ok = bool(msg) and json.loads(msg["Body"])["task_id"] == task
    if msg:
        sqs.delete(main, msg["ReceiptHandle"])
    check("mensagem entregue e confirmada", ok)
    check("fila vazia depois de confirmar", sqs.depth(main) == (0, 0), str(sqs.depth(main)))


def scenario_retry_and_dlq(sqs):
    print("C2: falha, nova tentativa com espera crescente e DLQ (maxReceiveCount=3)")
    main, dlq = make_queues(sqs, f"c2-{uuid.uuid4().hex[:6]}")
    sqs.send(main, json.dumps({"task_id": str(uuid.uuid4()), "always_fails": True}))
    stamps = []
    deadline = time.time() + 60
    while time.time() < deadline and len(stamps) < 3:
        msg = sqs.receive(main, wait=2)
        if not msg:
            continue
        stamps.append(time.time())
        count = int(msg["Attributes"]["ApproximateReceiveCount"])
        # worker falhou: espera crescente por tentativa (2 s, 4 s, ...)
        sqs.change_visibility(main, msg["ReceiptHandle"], 2 * count)
    gaps = [round(b - a, 1) for a, b in zip(stamps, stamps[1:])]
    check("3 tentativas antes da DLQ", len(stamps) == 3, f"tentativas={len(stamps)}")
    check("espera cresce entre tentativas", len(gaps) == 2 and gaps[1] > gaps[0], f"intervalos={gaps}")
    # No SQS, a mensagem só vai para a DLQ no próximo recebimento depois de estourar maxReceiveCount.
    in_dlq = None
    t0 = time.time()
    while time.time() - t0 < 30 and not in_dlq:
        sqs.receive(main, wait=2)  # dispara o redrive
        in_dlq = sqs.receive(dlq, wait=1)
    check("mensagem foi para a DLQ", bool(in_dlq))
    check("fila principal esvaziada", sqs.depth(main) == (0, 0), str(sqs.depth(main)))


def scenario_idempotency(sqs):
    print("C3: mesma mensagem duas vezes não duplica o efeito")
    main, _ = make_queues(sqs, f"c3-{uuid.uuid4().hex[:6]}")
    task = str(uuid.uuid4())
    body = json.dumps({"task_id": task})
    sqs.send(main, body)
    sqs.send(main, body)  # duplicada de propósito
    processed, effects = set(), 0
    for _ in range(6):
        msg = sqs.receive(main, wait=1)
        if not msg:
            break
        tid = json.loads(msg["Body"])["task_id"]
        if tid not in processed:  # consumidor idempotente
            processed.add(tid)
            effects += 1
        sqs.delete(main, msg["ReceiptHandle"])
    check("duas entregas, um efeito", effects == 1, f"efeitos={effects}")


def main():
    name = sys.argv[1] if len(sys.argv) > 1 else ""
    if name not in ENDPOINTS:
        sys.exit("uso: run_scenarios.py <elasticmq|localstack>")
    sqs = Sqs(ENDPOINTS[name])
    print(f"== Emulador: {name} ({ENDPOINTS[name]})")
    for fn in (scenario_basic, scenario_retry_and_dlq, scenario_idempotency):
        fn(sqs)
    failed = [r for r in results if not r[1]]
    print(f"\nResumo {name}: {len(results) - len(failed)}/{len(results)} verificações OK")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
