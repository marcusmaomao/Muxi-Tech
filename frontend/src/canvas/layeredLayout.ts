export interface LayoutNode {
  id: string;
}

export interface LayoutEdge {
  id: string;
  source: string;
  target: string;
}

export interface LayoutPoint {
  id: string;
  x: number;
  y: number;
}

/** 分层布局：边从父类指向子类时，父类在上。ELK 不可用时画布 Worker 用它兜底。 */
export function layeredLayout(nodes: LayoutNode[], edges: LayoutEdge[]): LayoutPoint[] {
  const rank = new Map<string, number>();
  nodes.forEach((node) => rank.set(node.id, 0));
  for (let pass = 0; pass < nodes.length; pass += 1) {
    for (const edge of edges) {
      const parent = rank.get(edge.source) ?? 0;
      const child = rank.get(edge.target) ?? 0;
      if (child < parent + 1) {
        rank.set(edge.target, parent + 1);
      }
    }
  }
  const columns = new Map<number, string[]>();
  for (const node of nodes) {
    const level = rank.get(node.id) ?? 0;
    const column = columns.get(level) ?? [];
    column.push(node.id);
    columns.set(level, column);
  }
  const points: LayoutPoint[] = [];
  for (const [level, column] of columns) {
    column.forEach((id, index) => {
      points.push({ id, x: 48 + index * 220, y: 48 + level * 140 });
    });
  }
  return points;
}
