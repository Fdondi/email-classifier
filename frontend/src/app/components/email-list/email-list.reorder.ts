export type EmailReorderAxis = 'urgency' | 'importance' | 'list';

export function moveItem<T>(items: T[], from: number, to: number): T[] {
  if (from === to || from < 0 || to < 0 || from >= items.length || to >= items.length) {
    return [...items];
  }
  const next = [...items];
  const [removed] = next.splice(from, 1);
  if (removed === undefined) {
    return [...items];
  }
  next.splice(to, 0, removed);
  return next;
}

/** Replace `group` members in `items` with `nextGroup` order, leaving other rows in place. */
export function spliceGroup<T extends { id: number }>(items: T[], group: T[], nextGroup: T[]): T[] {
  const queue = [...nextGroup];
  const inGroup = new Set(group.map((item) => item.id));
  return items.map((item) => {
    if (!inGroup.has(item.id)) {
      return item;
    }
    const next = queue.shift();
    return next ?? item;
  });
}

/**
 * Score for a row inserted between neighbors in a descending list.
 * `before` is the item above (higher score); `after` is the item below (lower score).
 */
export function insertScore(before: number | undefined, after: number | undefined): number {
  if (before == null && after == null) {
    return 0.5;
  }
  if (before == null) {
    const below = after ?? 0;
    return below >= 1 ? 1 : (below + 1) / 2;
  }
  if (after == null) {
    return before <= 0 ? 0 : before / 2;
  }
  return (before + after) / 2;
}
