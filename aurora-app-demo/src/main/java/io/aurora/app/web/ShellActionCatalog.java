package io.aurora.app.web;

import io.aurora.contract.UnifiedResult;
import io.aurora.rbac.Principal;
import io.aurora.retrieval.ActionCatalog;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Actions surfaced by the shell. They are filtered by the same permission
 * predicate as desktop icons and menu entries (AC-20).
 */
@Component
public class ShellActionCatalog implements ActionCatalog {

    @Override
    public List<UnifiedResult> actions(Principal principal) {
        return List.of(
                UnifiedResult.action("打开审批队列", "查看待处理与已完成的 Tool 审批", 0.95,
                        "approval:read", "approvals"),
                UnifiedResult.action("新建或管理用户", "user.create / user.update 经 Gateway 进入审批", 0.94,
                        "user:read", "directory"),
                UnifiedResult.action("查看数据流", "从种子数据到审批与审计的闭环导览", 0.92,
                        "file:read", "guide"),
                UnifiedResult.action("申请权限", "申请一个角色，由其他角色审批", 0.90,
                        "file:read", "access"),
                UnifiedResult.action("让 Agent 执行任务", "以你的权限为上限，写操作进入审批", 0.93,
                        "agent:use", "agent"),
                UnifiedResult.action("回放审计轨迹", "按 traceId 重建权限与 Tool 序列", 0.91,
                        "audit:read", "audit"),
                UnifiedResult.action("浏览对象", "在访达中查看你可访问的用户与文件", 0.89,
                        "file:read", "finder"),
                UnifiedResult.action("查看 Tool 注册表", "所有可被 AI 调用的能力与副作用", 0.87,
                        "tool:read", "tools"),
                UnifiedResult.action("管理插件", "受控扩展点、挂载点与生命周期", 0.85,
                        "plugin:manage", "plugins"),
                UnifiedResult.action("调整外观与玻璃", "玻璃 token、主题与 FPS 守卫", 0.82,
                        "glass:use", "glass"))
                .stream()
                .filter(a -> principal.can(a.requiredPermission()))
                .toList();
    }
}
