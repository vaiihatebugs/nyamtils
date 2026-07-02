// NyamTils party-sync relay.
//
// A stateless WebSocket relay: clients connect with ?c=<channel> (the dungeon's mini-server id,
// which every party member shares) and any message they send is forwarded to the other clients on
// the same channel. It stores nothing and reads nothing — it only relays bytes between teammates.
//
// Run:   npm install && node server.js        (listens on PORT, default 8080)
// Deploy: any host that gives you a public URL (Railway / Render / Fly / a VPS). See README.md.

const { WebSocketServer } = require("ws");

const PORT = process.env.PORT || 8080;
const wss = new WebSocketServer({ port: PORT });

/** channel -> Set<WebSocket> */
const channels = new Map();

wss.on("connection", (ws, req) => {
  let channel;
  try {
    channel = new URL(req.url, "http://x").searchParams.get("c");
  } catch {
    channel = null;
  }
  if (!channel) {
    ws.close();
    return;
  }

  let peers = channels.get(channel);
  if (!peers) channels.set(channel, (peers = new Set()));
  peers.add(ws);

  ws.on("message", (data) => {
    const text = data.toString();
    for (const peer of peers) {
      if (peer !== ws && peer.readyState === 1) peer.send(text);
    }
  });

  ws.on("close", () => {
    peers.delete(ws);
    if (peers.size === 0) channels.delete(channel);
  });

  ws.on("error", () => {});
});

console.log("NyamTils relay listening on :" + PORT);
