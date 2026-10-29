// Servidor de desenvolvimento para o emulador: escuta em todas as interfaces (o emulador acessa por 10.0.2.2).
import { createServer } from "./server.ts";
const s = createServer({ dev: true });
const port = await s.listen(8787, "0.0.0.0");
console.log(`servidor de teste em 0.0.0.0:${port}`);
