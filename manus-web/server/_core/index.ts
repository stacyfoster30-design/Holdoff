import "dotenv/config";
import express from "express";
import { createServer } from "http";
import net from "net";
import { createExpressMiddleware } from "@trpc/server/adapters/express";
import { registerOAuthRoutes } from "./oauth";
import { registerStorageProxy } from "./storageProxy";
import { appRouter } from "../routers";
import { createContext } from "./context";
import { serveStatic, setupVite } from "./vite";

function isPortAvailable(port: number): Promise<boolean> {
  return new Promise(resolve => {
    const server = net.createServer();
    server.listen(port, () => {
      server.close(() => resolve(true));
    });
    server.on("error", () => resolve(false));
  });
}

async function findAvailablePort(startPort: number = 3000): Promise<number> {
  for (let port = startPort; port < startPort + 20; port++) {
    if (await isPortAvailable(port)) {
      return port;
    }
  }
  throw new Error(`No available port found starting from ${startPort}`);
}

async function startServer() {
  const app = express();
  const server = createServer(app);
  // Configure body parser with larger size limit for file uploads
  app.use(express.json({ limit: "50mb" }));
  app.use(express.urlencoded({ limit: "50mb", extended: true }));
  registerStorageProxy(app);
  registerOAuthRoutes(app);
  // ── APK download redirect — points to latest GitHub Release APK ──────────
  app.get(["/api/download/android", "/android-app.apk"], async (_req, res) => {
    try {
      const https = await import("https");
      const options = {
        hostname: "api.github.com",
        path: "/repos/stacyfoster30-design/Holdoff/releases/latest",
        headers: { "User-Agent": "HoldOff-App/1.0" },
      };
      https.default.get(options, (apiRes: any) => {
        let data = "";
        apiRes.on("data", (chunk: any) => (data += chunk));
        apiRes.on("end", () => {
          try {
            const release = JSON.parse(data);
            const apkAsset = (release.assets || []).find((a: any) => a.name.endsWith(".apk"));
            if (apkAsset) {
              res.redirect(301, apkAsset.browser_download_url);
            } else {
              res.redirect(302, "/?building=1");
            }
          } catch {
            res.redirect(302, "/?building=1");
          }
        });
      }).on("error", () => res.redirect(302, "/?building=1"));
    } catch {
      res.redirect(302, "/?building=1");
    }
  });

  // ── Health check ──────────────────────────────────────────────────────────
  app.get(["/health", "/api/health"], (_req, res) => {
    res.json({ status: "healthy", version: process.env.npm_package_version || "1.0.0" });
  });

  // tRPC API
  app.use(
    "/api/trpc",
    createExpressMiddleware({
      router: appRouter,
      createContext,
    })
  );
  // development mode uses Vite, production mode uses static files
  if (process.env.NODE_ENV === "development") {
    await setupVite(app, server);
  } else {
    serveStatic(app);
  }

  const preferredPort = parseInt(process.env.PORT || "3000");
  const port = await findAvailablePort(preferredPort);

  if (port !== preferredPort) {
    console.log(`Port ${preferredPort} is busy, using port ${port} instead`);
  }

  server.listen(port, () => {
    console.log(`Server running on http://localhost:${port}/`);
  });
}

startServer().catch(console.error);
