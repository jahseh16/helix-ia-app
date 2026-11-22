package com.example.ui

import com.example.agent.runtime.AgentPhase
import com.example.agent.runtime.ApprovalRequest

/** Estado de una herramienta que todavía no tiene resultado guardado. */
enum class LiveToolState { AWAITING_APPROVAL, RUNNING }

/** Estado en vivo del agente (lo que aún no está en la base de datos). */
data class AgentUiState(
    val phase: AgentPhase = AgentPhase.IDLE,
    val step: Int = 0,
    val maxSteps: Int = 0,
    val streamingText: String = "",
    val streamingReasoning: String = "",
    val liveTools: Map<String, LiveToolState> = emptyMap(),
    val pendingApproval: ApprovalRequest? = null,
    val notice: String? = null
) {
    val isBusy: Boolean
        get() = phase == AgentPhase.STREAMING || phase == AgentPhase.AWAITING_APPROVAL || phase == AgentPhase.RUNNING_TOOL
}
