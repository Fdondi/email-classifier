import { insertScore, moveItem, spliceGroup } from './email-list.reorder';

describe('email-list reorder helpers', () => {
  it('moves an item to a new index', () => {
    expect(moveItem(['a', 'b', 'c'], 0, 2)).toEqual(['b', 'c', 'a']);
    expect(moveItem(['a', 'b', 'c'], 2, 0)).toEqual(['c', 'a', 'b']);
  });

  it('leaves the list unchanged when indexes match or are out of range', () => {
    expect(moveItem(['a', 'b'], 1, 1)).toEqual(['a', 'b']);
    expect(moveItem(['a', 'b'], -1, 0)).toEqual(['a', 'b']);
  });

  it('reorders only the group members inside a mixed list', () => {
    const items = [{ id: 1 }, { id: 2 }, { id: 3 }];
    const group = [{ id: 1 }, { id: 3 }];
    const nextGroup = [{ id: 3 }, { id: 1 }];
    expect(spliceGroup(items, group, nextGroup)).toEqual([{ id: 3 }, { id: 2 }, { id: 1 }]);
  });

  it('places a dropped row between neighboring scores', () => {
    expect(insertScore(0.8, 0.4)).toBeCloseTo(0.6);
    expect(insertScore(undefined, 0.4)).toBeCloseTo(0.7);
    expect(insertScore(0.4, undefined)).toBeCloseTo(0.2);
    expect(insertScore(undefined, undefined)).toBe(0.5);
    expect(insertScore(undefined, 1)).toBe(1);
    expect(insertScore(0, undefined)).toBe(0);
  });
});
