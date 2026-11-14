export function isAbsoluteNamespace(value: string) {
  const text = value.trim();
  if (!(text.endsWith("#") || text.endsWith("/"))) {
    return false;
  }
  try {
    return new URL(text).protocol !== "";
  } catch {
    return false;
  }
}
