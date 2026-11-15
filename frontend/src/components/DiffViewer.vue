<script setup lang="ts">
export interface FieldChange {
  op: string;
  path: string;
  before?: unknown;
  after?: unknown;
}

export interface ElementDiff {
  uid: string;
  elementType: string;
  apiName?: string;
  fields: FieldChange[];
}

export interface DiffResult {
  added: ElementDiff[];
  modified: ElementDiff[];
  deleted: ElementDiff[];
}

defineProps<{ diff: DiffResult | null }>();

function text(value: unknown) {
  if (value == null) {
    return "";
  }
  return typeof value === "string" ? value : JSON.stringify(value);
}
</script>

<template>
  <section v-if="diff" class="diff" data-testid="diff-viewer">
    <h3>新增</h3>
    <ul data-testid="diff-added">
      <li v-for="row in diff.added" :key="row.uid">{{ row.elementType }} · {{ row.apiName || row.uid }}</li>
      <li v-if="diff.added.length === 0">无</li>
    </ul>
    <h3>修改</h3>
    <ul data-testid="diff-modified">
      <li v-for="row in diff.modified" :key="row.uid">
        <strong>{{ row.elementType }} · {{ row.apiName || row.uid }}</strong>
        <span v-for="field in row.fields" :key="field.path" class="field">
          {{ field.op }} {{ field.path }}
          <template v-if="text(field.before)"> {{ text(field.before) }} → </template>
          {{ text(field.after) }}
        </span>
      </li>
      <li v-if="diff.modified.length === 0">无</li>
    </ul>
    <h3>删除</h3>
    <ul data-testid="diff-deleted">
      <li v-for="row in diff.deleted" :key="row.uid">{{ row.elementType }} · {{ row.apiName || row.uid }}</li>
      <li v-if="diff.deleted.length === 0">无</li>
    </ul>
  </section>
  <p v-else data-testid="diff-viewer">还没有比较。</p>
</template>

<style scoped>
.diff { background: #fff; border: 1px solid var(--line); border-radius: 12px; padding: 12px 16px; }
ul { margin: 0 0 12px; padding-left: 18px; }
.field { display: block; color: var(--ink-soft); }
</style>
