// Copyright 2025, OpenRemote Inc.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as
// published by the Free Software Foundation, either version 3 of the
// License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//
// SPDX-License-Identifier: AGPL-3.0-or-later

import { createServer } from 'node:http';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import sirv from 'sirv';
import { startServiceRegistrar } from './or-service-registrar.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const PORT = Number(process.env.PORT) || 8004;
const STATIC_DIR = process.env.STATIC_DIR || resolve(__dirname, '../../frontend/dist');
const ROOT_PATH = process.env.ROOT_PATH ?? '/services/hawkbit/ui';

// Browser traffic uses the published demo URLs, while the registrar continues
// to use OR_MANAGER_URL and KEYCLOAK_URL on the private Compose network.
const runtimeConfig = {
    orManagerUrl: process.env.OR_MANAGER_BROWSER_URL ?? process.env.OR_MANAGER_URL ?? 'http://localhost:8080',
    keycloakUrl: process.env.KEYCLOAK_BROWSER_URL ?? process.env.KEYCLOAK_URL ?? 'http://localhost:8081/auth'
};

let indexHtml;
try {
    indexHtml = readFileSync(resolve(STATIC_DIR, 'index.html'), 'utf8').replace(
        '__WEB_APP_CONFIG__',
        JSON.stringify(runtimeConfig)
    );
} catch {
    throw new Error(`Frontend build not found at ${STATIC_DIR}. Run 'npm run build' first.`);
}

const assets = sirv(STATIC_DIR, { single: false, etag: true });

function handler(req, res) {
    const { pathname, search } = new URL(req.url ?? '/', 'http://x');
    const path = pathname.startsWith(ROOT_PATH) ? pathname.slice(ROOT_PATH.length) || '/' : pathname;

    if (path === '/' || path === '/index.html') {
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
        res.end(indexHtml);
        return;
    }

    const originalUrl = req.url;
    req.url = path + search;
    assets(req, res, () => {
        req.url = originalUrl;
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
        res.end(indexHtml);
    });
}

const server = createServer(handler);
server.on('error', (err) => {
    console.error(`Server error: ${err.message}`);
    process.exit(1);
});
server.listen(PORT, () => console.log(`Listening on :${PORT}`));

try {
    await startServiceRegistrar(() => server.close(() => process.exit(0)));
} catch (err) {
    console.error('Failed to register service with OpenRemote:', err);
    server.close(() => process.exit(1));
}
