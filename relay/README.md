# NyamTils party-sync relay

A tiny WebSocket relay so party members running NyamTils can share dungeon room IDs (which gives
everyone room names + secret counts for rooms a *teammate* explored). It is stateless: it groups
connections by channel (the dungeon's mini-server id) and forwards each message to the others on the
same channel. It stores nothing and never connects to Hypixel.

## Run locally (testing)

```
cd relay
npm install
node server.js        # listens on ws://localhost:8080
```

Then in-game: NyamTils config → Dungeon Map → set **Sync URL** to `ws://localhost:8080` and enable
**Party sync**. (Local only works if everyone is on your machine — for a real party it must be hosted, see below.)

## Host it (so your whole party can reach it)

It needs one public URL everyone can connect to. Any of these work; pick one:

- **Railway / Render / Fly.io (free tier):** create a Node service from this `relay/` folder. The
  start command is `node server.js`; it reads `PORT` from the environment automatically. You'll get a
  URL like `your-app.up.railway.app` → use `wss://your-app.up.railway.app` as the Sync URL.
- **A VPS you own:** `npm install && node server.js`, put it behind a reverse proxy with TLS, and use
  `wss://your-domain`.

Then everyone in the party sets the same **Sync URL** + enables **Party sync**. Done — no accounts,
no keys, nothing stored.
