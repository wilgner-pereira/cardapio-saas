import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { extname, join, normalize } from "node:path";
import { pathToFileURL } from "node:url";

const port = Number(process.env.PORT || 5173);
const root = join(process.cwd(), "dist");
// Use the policy compiled with the same API origin as the frontend bundle.
// Missing policy is a deployment error: do not silently serve unprotected HTML.

const contentTypes = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".svg": "image/svg+xml",
  ".ico": "image/x-icon"
};

function resolvePath(url, root) {
  const pathname = new URL(url, `http://localhost:${port}`).pathname;
  const cleanPath = normalize(pathname).replace(/^(\.\.[/\\])+/, "");
  return join(root, cleanPath === "/" ? "index.html" : cleanPath);
}

export function createStaticServer(root, csp) {
  return createServer(async (request, response) => {
  try {
    let filePath = resolvePath(request.url || "/", root);
    let body;

    try {
      body = await readFile(filePath);
    } catch {
      filePath = join(root, "index.html");
      body = await readFile(filePath);
    }

    response.writeHead(200, {
      "Content-Security-Policy": csp,
      "Content-Type": contentTypes[extname(filePath)] || "application/octet-stream"
    });
    response.end(body);
  } catch {
    response.writeHead(500, { "Content-Type": "text/plain; charset=utf-8" });
    response.end("Erro ao servir o front-end");
  }
  });
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const csp = (await readFile(join(root, "csp-header.txt"), "utf8")).trim();
  createStaticServer(root, csp).listen(port, "0.0.0.0", () => {
    console.log(`Front-end em http://localhost:${port}`);
  });
}
