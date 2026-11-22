package com.example.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.connectors.github.RepoInfo
import com.example.ui.Brand
import com.example.ui.theme.*

/** `@texto` o `/texto` al final de lo escrito: lo que dispara las sugerencias de repositorios. */
data class MentionTrigger(val symbol: Char, val query: String, val start: Int)

object Mentions {
    private val QUERY = Regex("^[A-Za-z0-9_./-]*$")

    /**
     * Detecta el token que se está escribiendo. `@` funciona en cualquier palabra que EMPIECE por @ (así
     * `ana@correo.com` no dispara nada); `/` solo al principio del mensaje, para no molestar con rutas como /sdcard.
     */
    fun detect(text: String): MentionTrigger? {
        val start = text.indexOfLast { it.isWhitespace() } + 1
        val token = text.substring(start)
        if (token.isEmpty()) return null
        val symbol = token[0]
        val query = token.drop(1)
        if ((symbol != '@' && symbol != '/') || !QUERY.matches(query)) return null
        if (symbol == '/' && start != 0) return null
        return MentionTrigger(symbol, query, start)
    }

    /** Reemplaza el token por la referencia al repositorio elegida. */
    fun insert(text: String, trigger: MentionTrigger, repoFullName: String): String =
        text.substring(0, trigger.start) + "@$repoFullName "

    fun filter(repos: List<RepoInfo>, query: String, limit: Int = 30): List<RepoInfo> {
        val q = query.trim().removePrefix("@").lowercase()
        if (q.isEmpty()) return repos.take(limit)
        return repos
            .filter { it.fullName.lowercase().contains(q) }
            .sortedBy { r ->
                val n = r.fullName.lowercase()
                when {
                    n.substringAfter('/').startsWith(q) -> 0
                    n.startsWith(q) -> 1
                    else -> 2
                }
            }
            .take(limit)
    }
}

/** Estado de GitHub que la píldora necesita para sugerir repos o avisar de que no está conectado. */
data class RepoSuggestState(
    val connected: Boolean = false,
    val repos: List<RepoInfo> = emptyList(),
    val loading: Boolean = false,
    val onNeedRepos: () -> Unit = {},
    val onOpenConnectors: () -> Unit = {}
)

/** Panel glass sobre la píldora con los repositorios que coinciden, o el aviso "GitHub no conectado". */
@Composable
fun RepoSuggestionPanel(trigger: MentionTrigger, state: RepoSuggestState, onPick: (RepoInfo) -> Unit, modifier: Modifier = Modifier) {
    val matches = if (state.connected) Mentions.filter(state.repos, trigger.query) else emptyList()

    // No conectado: solo se avisa al teclear el símbolo (o @ + poco texto), para no estorbar en mensajes normales
    val showNotConnected = !state.connected && (trigger.query.isEmpty() || (trigger.symbol == '@' && trigger.query.length <= 12))
    if (state.connected && matches.isEmpty() && !state.loading) return
    if (!state.connected && !showNotConnected) return

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = AppSurface2,
        border = BorderStroke(1.dp, AppStroke),
        modifier = modifier.fillMaxWidth().padding(horizontal = 14.dp).testTag("repo_suggestions")
    ) {
        if (!state.connected) {
            Row(
                modifier = Modifier.clickable(onClick = state.onOpenConnectors).padding(14.dp).testTag("github_not_connected"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BrandLogo(Brand.GITHUB, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("GitHub no está conectado", style = BodyStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
                    Text("Conéctalo para referenciar tus repositorios", style = MetaStyle)
                }
                Text("Abrir Conectores", style = MetaStyle.copy(color = AppAccent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp))
            }
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 260.dp).padding(vertical = 6.dp)) {
                if (state.loading && matches.isEmpty()) {
                    item {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = AppAccent)
                            Spacer(Modifier.width(10.dp))
                            Text("Cargando tus repositorios…", style = MetaStyle)
                        }
                    }
                }
                items(matches, key = { it.fullName }) { repo ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onPick(repo) }.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BrandLogo(Brand.GITHUB, size = 30.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(repo.fullName, style = BodyStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            repo.description?.let { Text(it, style = MetaStyle.copy(fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                        if (repo.isPrivate) Icon(Icons.Default.Lock, "Privado", tint = AppTextMuted, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}
