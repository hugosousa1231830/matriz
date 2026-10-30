/**
 * Number of characters as Unicode code points, the way the API counts them
 * (`'😀'.length` is 2, `textLength('😀')` is 1).
 */
export function textLength(value: string): number {
  return Array.from(value).length
}
