package io.github.mello13256.fotos.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.mello13256.fotos.R
import io.github.mello13256.fotos.data.MediaSource
import io.github.mello13256.fotos.ui.albums.AlbumsScreen
import io.github.mello13256.fotos.ui.collection.CollectionScreen
import io.github.mello13256.fotos.ui.collection.FolderScreen
import io.github.mello13256.fotos.ui.common.LocalBottomBarSpace
import io.github.mello13256.fotos.ui.common.LocalNavAnimatedScope
import io.github.mello13256.fotos.ui.common.LocalSharedTransitionScope
import io.github.mello13256.fotos.ui.permission.MediaAccess
import io.github.mello13256.fotos.ui.photos.PhotosScreen
import io.github.mello13256.fotos.ui.settings.SettingsScreen
import io.github.mello13256.fotos.ui.trash.TrashScreen
import io.github.mello13256.fotos.ui.viewer.ViewerScreen

private object Routes {
    const val PHOTOS = "photos"
    const val ALBUMS = "albums"
    const val COLLECTION = "collection/{id}"
    const val FOLDER = "folder?path={path}&name={name}"
    const val VIEWER = "viewer?source={source}&id={id}"
    const val TRASH = "trash"
    const val SETTINGS = "settings"

    fun collection(id: Long) = "collection/$id"
    fun folder(path: String, name: String) = "folder?path=${Uri.encode(path)}&name=${Uri.encode(name)}"
    fun viewer(source: MediaSource, id: Long) = "viewer?source=${Uri.encode(source.encode())}&id=$id"
}

/** Chave onde o visualizador deixa a última fotografia vista, para a grelha a mostrar ao voltar. */
private const val FOCUS_KEY = "focus_media_id"

private val BottomBarHeight = 80.dp

private enum class Tab(val route: String, val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    PHOTOS(Routes.PHOTOS, R.string.tab_photos, Icons.Outlined.Photo, Icons.Filled.Photo),
    ALBUMS(Routes.ALBUMS, R.string.tab_albums, Icons.Outlined.PhotoLibrary, Icons.Filled.PhotoLibrary),
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FotosNavHost(access: MediaAccess, onAccessChanged: () -> Unit) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showTabs = Tab.entries.any { it.route == currentRoute }

    val openViewer: (MediaSource, Long) -> Unit = { source, id -> navController.navigate(Routes.viewer(source, id)) }

    // A barra inferior sobrepõe-se ao conteúdo (em vez de o encolher), para a grelha não mudar de
    // tamanho ao abrir o visualizador: a transição partilhada parte do sítio certo.
    Box(Modifier.fillMaxSize()) {
        SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.PHOTOS,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    screen(Routes.PHOTOS, tab = true) { entry ->
                        PhotosScreen(
                            access = access,
                            onAccessChanged = onAccessChanged,
                            onOpen = openViewer,
                            onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                            focusId = entry.savedStateHandle.get<Long>(FOCUS_KEY),
                            onFocusConsumed = { entry.savedStateHandle.remove<Long>(FOCUS_KEY) },
                        )
                    }
                    screen(Routes.ALBUMS, tab = true) {
                        AlbumsScreen(
                            onOpenCollection = { navController.navigate(Routes.collection(it)) },
                            onOpenFolder = { path, name -> navController.navigate(Routes.folder(path, name)) },
                            onOpenTrash = { navController.navigate(Routes.TRASH) },
                        )
                    }
                    screen(
                        Routes.COLLECTION,
                        arguments = listOf(navArgument("id") { type = NavType.LongType }),
                    ) { entry ->
                        CollectionScreen(
                            collectionId = entry.arguments?.getLong("id") ?: 0L,
                            onBack = { navController.popBackStack() },
                            onOpen = openViewer,
                            focusId = entry.savedStateHandle.get<Long>(FOCUS_KEY),
                            onFocusConsumed = { entry.savedStateHandle.remove<Long>(FOCUS_KEY) },
                        )
                    }
                    screen(
                        Routes.FOLDER,
                        arguments = listOf(
                            navArgument("path") { type = NavType.StringType; defaultValue = "" },
                            navArgument("name") { type = NavType.StringType; defaultValue = "" },
                        ),
                    ) { entry ->
                        FolderScreen(
                            relativePath = entry.arguments?.getString("path").orEmpty(),
                            name = entry.arguments?.getString("name").orEmpty(),
                            onBack = { navController.popBackStack() },
                            onOpen = openViewer,
                            focusId = entry.savedStateHandle.get<Long>(FOCUS_KEY),
                            onFocusConsumed = { entry.savedStateHandle.remove<Long>(FOCUS_KEY) },
                        )
                    }
                    screen(
                        Routes.VIEWER,
                        arguments = listOf(
                            navArgument("source") { type = NavType.StringType; defaultValue = "" },
                            navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                        ),
                    ) { entry ->
                        ViewerScreen(
                            source = MediaSource.decode(entry.arguments?.getString("source").orEmpty()),
                            startId = entry.arguments?.getLong("id") ?: 0L,
                            onBack = { navController.popBackStack() },
                            onCurrentChanged = { id ->
                                navController.previousBackStackEntry?.savedStateHandle?.set(FOCUS_KEY, id)
                            },
                        )
                    }
                    screen(Routes.TRASH) {
                        TrashScreen(onBack = { navController.popBackStack() })
                    }
                    screen(Routes.SETTINGS) {
                        SettingsScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showTabs,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    val selected = tab.route == currentRoute
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navController.switchTab(tab.route) },
                        icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        }
    }
}

/** Destino que expõe a sua animação às transições partilhadas e, nos separadores, o espaço da barra. */
private fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    tab: Boolean = false,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable(route, arguments) { entry ->
        CompositionLocalProvider(
            LocalNavAnimatedScope provides this,
            LocalBottomBarSpace provides if (tab) BottomBarHeight else 0.dp,
        ) {
            content(entry)
        }
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
