export function isApiName(value: string) {
  return /^[A-Za-z][A-Za-z0-9_]*$/.test(value.trim());
}
