"""Assertions over `tofu show -json` of the plan (CA18, RF10, ADR-0002)."""
import json
import sys

# Resource types RF10 allows: network without NAT, one EC2 instance, RDS, S3, SQS with alarms, SSM, and the
# IAM, KMS, SNS and random pieces those need.
ALLOWED = {
    "aws_vpc", "aws_internet_gateway", "aws_subnet", "aws_route_table", "aws_route", "aws_route_table_association",
    "aws_security_group", "aws_vpc_security_group_ingress_rule", "aws_vpc_security_group_egress_rule",
    "aws_instance", "aws_eip", "aws_iam_role", "aws_iam_role_policy", "aws_iam_role_policy_attachment",
    "aws_iam_instance_profile",
    "aws_db_instance", "aws_db_subnet_group", "aws_kms_key", "aws_kms_alias",
    "aws_s3_bucket", "aws_s3_bucket_public_access_block", "aws_s3_bucket_ownership_controls",
    "aws_s3_bucket_versioning", "aws_s3_bucket_server_side_encryption_configuration",
    "aws_s3_bucket_lifecycle_configuration", "aws_s3_bucket_cors_configuration", "aws_s3_bucket_policy",
    "aws_sqs_queue", "aws_sqs_queue_policy", "aws_sqs_queue_redrive_allow_policy",
    "aws_sns_topic", "aws_sns_topic_subscription", "aws_cloudwatch_metric_alarm",
    "aws_ssm_parameter", "random_password",
}


def main(path):
    plan = json.load(open(path))
    resources = plan["planned_values"]["root_module"]
    flat = []

    def walk(module):
        # Data sources (policy documents) create nothing; only managed resources count.
        flat.extend(r for r in module.get("resources", []) if r.get("mode") == "managed")
        for child in module.get("child_modules", []):
            walk(child)

    walk(resources)
    problems = []

    for r in flat:
        if r["type"] not in ALLOWED:
            problems.append(f"{r['address']}: type {r['type']} is outside RF10")

    def of(type_):
        return [r for r in flat if r["type"] == type_]

    if len(of("aws_instance")) != 1:
        problems.append("expected exactly one EC2 instance")
    for r in of("aws_instance"):
        v = r["values"]
        if not v["instance_type"].startswith(("t4g.", "m6g.", "m7g.", "c6g.", "c7g.", "r6g.", "r7g.")):
            problems.append(f"{r['address']}: instance type {v['instance_type']} is not ARM")
        if v["metadata_options"][0]["http_tokens"] != "required":
            problems.append(f"{r['address']}: IMDSv2 is not required")
        if not v["root_block_device"][0]["encrypted"]:
            problems.append(f"{r['address']}: root volume is not encrypted")

    for r in of("aws_db_instance"):
        v = r["values"]
        if v["publicly_accessible"]:
            problems.append(f"{r['address']}: database is public")
        if v["multi_az"]:
            problems.append(f"{r['address']}: ADR-0002 says single-AZ")
        if not v["storage_encrypted"]:
            problems.append(f"{r['address']}: database storage is not encrypted")

    for r in of("aws_vpc_security_group_ingress_rule"):
        v = r["values"]
        port = v["from_port"]
        if port not in (80, 443, 5432):
            problems.append(f"{r['address']}: unexpected ingress port {port} (no SSH; access is by Session Manager)")
        if port == 5432 and v.get("cidr_ipv4"):
            problems.append(f"{r['address']}: the database must be reachable only from the application security group")

    for r in of("aws_s3_bucket_public_access_block"):
        v = r["values"]
        if not all(v[k] for k in ("block_public_acls", "block_public_policy", "ignore_public_acls", "restrict_public_buckets")):
            problems.append(f"{r['address']}: public access is not fully blocked")

    queues = {r["values"]["name"]: r["values"] for r in of("aws_sqs_queue")}
    work = [n for n in queues if not n.endswith("-dlq")]
    if len(work) != 1 or f"{work[0]}-dlq" not in queues:
        problems.append("expected one work queue and its DLQ")
    for name, q in queues.items():
        if not q.get("sqs_managed_sse_enabled"):
            problems.append(f"queue {name} is not encrypted at rest")

    alarms = {r["values"]["metric_name"] for r in of("aws_cloudwatch_metric_alarm")}
    for needed in ("ApproximateAgeOfOldestMessage", "ApproximateNumberOfMessagesVisible"):
        if needed not in alarms:
            problems.append(f"missing alarm on {needed} (CA16)")

    for r in of("aws_ssm_parameter"):
        if r["values"]["type"] != "SecureString":
            problems.append(f"{r['address']}: secrets must be SecureString")

    if problems:
        print("The plan breaks RF10 / ADR-0002:")
        for p in problems:
            print(f"  - {p}")
        sys.exit(1)

    counts = {}
    for r in flat:
        counts[r["type"]] = counts.get(r["type"], 0) + 1
    print(f"OK: {len(flat)} resources, all within RF10")
    for t in sorted(counts):
        print(f"  {counts[t]:>2}  {t}")


main(sys.argv[1])
