/** ForgeOps Widget 自包含样式（与三栈壳共享的唯一 UI 实现）。 */
export const WIDGET_CSS = `
.forgeops-root { position: static; }
.forgeops-root, .forgeops-root * { box-sizing: border-box; font-family: -apple-system, 'PingFang SC', 'Microsoft YaHei', sans-serif; }
.forgeops-fab {
  position: fixed; right: 24px; bottom: 24px; z-index: 2147483000;
  background: #2563eb; color: #fff; border: none; border-radius: 24px;
  padding: 10px 22px; font-size: 14px; cursor: pointer; box-shadow: 0 4px 14px rgba(37,99,235,.4);
}
.forgeops-fab:hover { background: #1d4ed8; }
.forgeops-panel {
  position: fixed; right: 24px; bottom: 76px; z-index: 2147483000;
  width: 420px; max-width: calc(100vw - 32px); max-height: min(78vh, 720px);
  background: #fff; border-radius: 12px; box-shadow: 0 12px 40px rgba(0,0,0,.18);
  display: flex; flex-direction: column; overflow: hidden;
}
.forgeops-header { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; border-bottom: 1px solid #e5e7eb; }
.forgeops-title { font-weight: 600; font-size: 15px; color: #111827; }
.forgeops-close { background: none; border: none; cursor: pointer; font-size: 14px; color: #6b7280; padding: 4px 8px; }
.forgeops-tabs { display: flex; border-bottom: 1px solid #e5e7eb; }
.forgeops-tab { flex: 1; padding: 10px; background: none; border: none; font-size: 13px; color: #6b7280; cursor: pointer; border-bottom: 2px solid transparent; }
.forgeops-tab.active { color: #2563eb; border-bottom-color: #2563eb; font-weight: 600; }
.forgeops-body { padding: 14px 16px; overflow-y: auto; display: flex; flex-direction: column; gap: 10px; font-size: 13px; color: #111827; }
.forgeops-field { display: flex; flex-direction: column; gap: 4px; font-size: 12px; color: #374151; }
.forgeops-field input, .forgeops-field select, .forgeops-field textarea {
  border: 1px solid #d1d5db; border-radius: 6px; padding: 7px 9px; font-size: 13px; background: #fff; width: 100%;
}
.forgeops-field input:focus, .forgeops-field textarea:focus, .forgeops-field select:focus { outline: 2px solid #93c5fd; border-color: #2563eb; }
.forgeops-required { color: #dc2626; }
.forgeops-details-toggle { cursor: pointer; color: #6b7280; font-size: 12px; }
.forgeops-pre {
  background: #f9fafb; border: 1px solid #e5e7eb; border-radius: 6px; padding: 8px;
  font-size: 11px; max-height: 200px; overflow: auto; white-space: pre-wrap; word-break: break-all;
  font-family: ui-monospace, Menlo, Consolas, monospace; margin: 4px 0 0;
}
.forgeops-submit { background: #2563eb; color: #fff; border: none; border-radius: 8px; padding: 10px; font-size: 14px; cursor: pointer; }
.forgeops-submit:disabled { background: #93c5fd; cursor: not-allowed; }
.forgeops-error { background: #fef2f2; color: #b91c1c; border: 1px solid #fecaca; border-radius: 6px; padding: 8px; font-size: 12px; }
.forgeops-success { background: #f0fdf4; color: #15803d; border: 1px solid #bbf7d0; border-radius: 6px; padding: 8px; font-size: 12px; }
.forgeops-row { display: flex; gap: 8px; }
.forgeops-row input { flex: 1; border: 1px solid #d1d5db; border-radius: 6px; padding: 7px 9px; font-size: 13px; }
.forgeops-row button { background: #f3f4f6; border: 1px solid #d1d5db; border-radius: 6px; padding: 7px 12px; cursor: pointer; font-size: 13px; }
.forgeops-item { border: 1px solid #e5e7eb; border-radius: 8px; padding: 10px; cursor: pointer; }
.forgeops-item:hover { border-color: #93c5fd; background: #f8fafc; }
.forgeops-item-line { display: flex; justify-content: space-between; margin-bottom: 4px; }
.forgeops-item-id { color: #6b7280; font-size: 12px; }
.forgeops-empty { color: #9ca3af; text-align: center; padding: 20px 0; }
.forgeops-status { font-size: 11px; padding: 2px 8px; border-radius: 10px; white-space: nowrap; }
.forgeops-st-todo { background: #f3f4f6; color: #6b7280; }
.forgeops-st-doing { background: #eff6ff; color: #2563eb; }
.forgeops-st-verify { background: #fef3c7; color: #b45309; }
.forgeops-st-done { background: #f0fdf4; color: #15803d; }
.forgeops-st-info { background: #fdf2f8; color: #be185d; }
.forgeops-back { background: none; border: none; color: #2563eb; cursor: pointer; font-size: 13px; padding: 0; align-self: flex-start; }
.forgeops-detail-title { margin: 2px 0 6px; font-size: 14px; color: #111827; font-weight: 600; }
.forgeops-meta { font-size: 12px; color: #374151; word-break: break-all; }
.forgeops-meta a { color: #2563eb; }
.forgeops-timeline { display: flex; flex-direction: column; gap: 8px; max-height: 240px; overflow-y: auto; }
.forgeops-comment { border: 1px solid #e5e7eb; border-radius: 8px; padding: 8px 10px; }
.forgeops-comment-head { display: flex; justify-content: space-between; font-size: 11px; color: #6b7280; margin-bottom: 4px; gap: 8px; }
.forgeops-comment-body { font-size: 12px; white-space: pre-wrap; word-break: break-word; color: #111827; }
.forgeops-verify { display: flex; gap: 8px; margin-top: 4px; }
.forgeops-pass { flex: 1; background: #16a34a; color: #fff; border: none; border-radius: 8px; padding: 9px; cursor: pointer; font-size: 13px; }
.forgeops-fail { flex: 1; background: #dc2626; color: #fff; border: none; border-radius: 8px; padding: 9px; cursor: pointer; font-size: 13px; }
`
