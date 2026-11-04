package com.ila3.dshnative.ui

/** One bottom-navigation entry; `locked` entries cannot be hidden. */
data class NavItem(val id: String, val label: String, val locked: Boolean = false)

val DefaultNavItems: List<NavItem> = listOf(
    NavItem("now", "现在", locked = true),
    NavItem("tasks", "任务"),
    NavItem("files", "文件"),
    NavItem("settings", "设置"),
    NavItem("automation", "自动化"),
    NavItem("team", "团队"),
)

val NavById: Map<String, NavItem> = DefaultNavItems.associateBy { it.id }
