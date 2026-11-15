import ELK from "elkjs/lib/elk.bundled.js";
import { layeredLayout, type LayoutEdge, type LayoutNode, type LayoutPoint } from "./layeredLayout";

interface Request {
  nodes: LayoutNode[];
  edges: LayoutEdge[];
}

const elk = new ELK();

self.onmessage = (event: MessageEvent<Request>) => {
  const data = event.data;
  void elk
    .layout({
      id: "root",
      layoutOptions: {
        "elk.algorithm": "layered",
        "elk.direction": "DOWN",
        "elk.spacing.nodeNode": "48",
      },
      children: data.nodes.map((node) => ({ id: node.id, width: 180, height: 64 })),
      edges: data.edges.map((edge) => ({ id: edge.id, sources: [edge.source], targets: [edge.target] })),
    })
    .then((result) => {
      const positions: LayoutPoint[] = (result.children ?? []).map((child) => ({
        id: child.id,
        x: child.x ?? 0,
        y: child.y ?? 0,
      }));
      self.postMessage(positions);
    })
    .catch(() => {
      self.postMessage(layeredLayout(data.nodes, data.edges));
    });
};
