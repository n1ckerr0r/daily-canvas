package com.n1ckerr0r.dailycanvas.ui

import android.content.Intent
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Palette
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import coil.ImageLoader
import coil.request.ImageRequest
import com.n1ckerr0r.dailycanvas.R
import com.n1ckerr0r.dailycanvas.data.remote.NetworkModule
import com.n1ckerr0r.dailycanvas.ui.model.ArtworkCard
import com.n1ckerr0r.dailycanvas.ui.model.ArtworkTag
import com.n1ckerr0r.dailycanvas.ui.model.CalendarArtwork
import com.n1ckerr0r.dailycanvas.ui.model.SettingsPayload
import com.n1ckerr0r.dailycanvas.ui.theme.AccentRed
import com.n1ckerr0r.dailycanvas.ui.theme.WarmPaper
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private sealed class AppDestination(val route: String, val label: String) {
    data object Auth : AppDestination("auth", "Вход")
    data object Home : AppDestination("home", "Сегодня")
    data object Gallery : AppDestination("gallery", "Галерея")
    data object Favorites : AppDestination("favorites", "Избранное")
    data object Calendar : AppDestination("calendar", "Календарь")
    data object Settings : AppDestination("settings", "Настройки")
    data object Detail : AppDestination("detail/{artworkId}", "Картина")
}

@Composable
fun DailyCanvasApp(
    viewModel: DailyCanvasViewModel = viewModel(),
) {
    val navController = rememberNavController()
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var galleryTag by rememberSaveable { mutableStateOf<String?>(null) }
    var galleryArtist by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val session = remember { context.getSharedPreferences("daily_canvas_session", Context.MODE_PRIVATE) }
    var artworkGridMode by rememberSaveable { mutableStateOf(session.getBoolean("artwork_grid_mode", false)) }
    var profileEmail by remember { mutableStateOf(NetworkModule.session.email ?: "") }
    val hasSession = remember { NetworkModule.session.hasSession() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    LaunchedEffect(state.isAuthenticated) {
        if (!state.isAuthenticated && navController.currentDestination?.route != AppDestination.Auth.route) {
            navController.navigate(AppDestination.Auth.route) { popUpTo(0) }
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (hasSession) AppDestination.Home.route else AppDestination.Auth.route,
        modifier = Modifier
            .fillMaxSize()
            .background(WarmPaper),
    ) {
        composable(AppDestination.Auth.route) {
            AuthScreen(
                isLoading = state.isLoading,
                onContinue = { email, password, name, register ->
                    viewModel.authenticate(email, password, name, register) { authenticatedEmail ->
                        profileEmail = authenticatedEmail
                        navController.navigate(AppDestination.Home.route) { popUpTo(AppDestination.Auth.route) { inclusive = true } }
                    }
                },
            )
        }
        composable(AppDestination.Home.route) {
            MainScaffold(
                current = AppDestination.Home,
                snackbarHostState = snackbarHostState,
                onNavigate = { destination ->
                    navController.navigate(destination.route) {
                        popUpTo(AppDestination.Home.route)
                        launchSingleTop = true
                    }
                },
            ) { padding ->
                HomeScreen(
                    modifier = Modifier.padding(padding),
                    state = state,
                    onOpenArtwork = {
                        viewModel.openArtwork(it)
                        navController.navigate("detail/$it")
                    },
                    onFavoriteToggle = viewModel::toggleFavorite,
                    onTagClick = { tag ->
                      пше  galleryTag = tag.id
                        galleryArtist = null
                        navController.navigate(AppDestination.Gallery.route)
                    },
                    onArtistClick = { artistId ->
                        galleryArtist = artistId
                        galleryTag = null
                        navController.navigate(AppDestination.Gallery.route)
                    },
                    onOpenCalendar = { navController.navigate(AppDestination.Calendar.route) },
                    onOpenSettings = { navController.navigate(AppDestination.Settings.route) },
                )
            }
        }
        composable(AppDestination.Calendar.route) {
            MainScaffold(
                current = AppDestination.Calendar,
                snackbarHostState = snackbarHostState,
                onNavigate = { destination ->
                    navController.navigate(destination.route) {
                        popUpTo(AppDestination.Home.route)
                        launchSingleTop = true
                    }
                },
            ) { padding ->
                CalendarScreen(
                    modifier = Modifier.padding(padding),
                    state = state,
                    onInitialize = viewModel::initializeCalendar,
                    onLoadMonth = viewModel::loadCalendar,
                    onOpenArtwork = {
                        viewModel.openArtwork(it)
                        navController.navigate("detail/$it")
                    },
                    onBack = { navController.popBackStack() },
                    onOpenSettings = { navController.navigate(AppDestination.Settings.route) },
                )
            }
        }
        composable(AppDestination.Gallery.route) {
            MainScaffold(
                current = AppDestination.Gallery,
                snackbarHostState = snackbarHostState,
                onNavigate = { destination ->
                    navController.navigate(destination.route) {
                        popUpTo(AppDestination.Home.route)
                        launchSingleTop = true
                    }
                },
            ) { padding ->
                GalleryScreen(
                    modifier = Modifier.padding(padding),
                    state = state,
                    tagFilter = galleryTag,
                    artistFilter = galleryArtist,
                    onClearFilter = {
                        galleryTag = null
                        galleryArtist = null
                    },
                    onOpenArtwork = {
                        viewModel.openArtwork(it)
                        navController.navigate("detail/$it")
                    },
                    onFavoriteToggle = viewModel::toggleFavorite,
                    onOpenSettings = { navController.navigate(AppDestination.Settings.route) },
                    gridMode = artworkGridMode,
                    onGridModeChanged = {
                        artworkGridMode = it
                        session.edit().putBoolean("artwork_grid_mode", it).apply()
                    },
                )
            }
        }
        composable(AppDestination.Favorites.route) {
            MainScaffold(
                current = AppDestination.Favorites,
                snackbarHostState = snackbarHostState,
                onNavigate = { destination ->
                    navController.navigate(destination.route) {
                        popUpTo(AppDestination.Home.route)
                        launchSingleTop = true
                    }
                },
            ) { padding ->
                FavoritesScreen(
                    modifier = Modifier.padding(padding),
                    state = state,
                    onOpenArtwork = {
                        viewModel.openArtwork(it)
                        navController.navigate("detail/$it")
                    },
                    onFavoriteToggle = viewModel::toggleFavorite,
                    onOpenSettings = { navController.navigate(AppDestination.Settings.route) },
                    gridMode = artworkGridMode,
                    onGridModeChanged = {
                        artworkGridMode = it
                        session.edit().putBoolean("artwork_grid_mode", it).apply()
                    },
                )
            }
        }
        composable(AppDestination.Settings.route) {
            MainScaffold(
                current = AppDestination.Settings,
                snackbarHostState = snackbarHostState,
                onNavigate = { destination ->
                    navController.navigate(destination.route) {
                        popUpTo(AppDestination.Home.route)
                        launchSingleTop = true
                    }
                },
            ) { padding ->
                SettingsScreen(
                    modifier = Modifier.padding(padding),
                    settings = state.payload?.settings,
                    profileEmail = profileEmail,
                    onNotificationsChanged = viewModel::updateNotifications,
                    onCollectionsChanged = viewModel::updateCollections,
                    onPasswordChange = { currentPassword, newPassword ->
                        viewModel.changePassword(currentPassword, newPassword) {
                            navController.navigate(AppDestination.Auth.route) { popUpTo(0) }
                        }
                    },
                    onBack = { navController.popBackStack() },
                    onLogout = {
                        viewModel.logout {
                            navController.navigate(AppDestination.Auth.route) { popUpTo(0) }
                        }
                    },
                    onDeleteAccount = {
                        viewModel.deleteAccount {
                            navController.navigate(AppDestination.Auth.route) { popUpTo(0) }
                        }
                    },
                )
            }
        }
        composable(
            route = AppDestination.Detail.route,
            arguments = listOf(navArgument("artworkId") { type = NavType.StringType }),
        ) {
            DetailScreen(
                artwork = state.selectedArtwork,
                onBack = { navController.popBackStack() },
                onFavoriteToggle = viewModel::toggleFavorite,
                onTagClick = { tag ->
                    galleryTag = tag.id
                    galleryArtist = null
                    navController.navigate(AppDestination.Gallery.route)
                },
                onArtistClick = { artistId ->
                    galleryArtist = artistId
                    galleryTag = null
                    navController.navigate(AppDestination.Gallery.route)
                },
                onOpenSettings = { navController.navigate(AppDestination.Settings.route) },
            )
        }
    }
}

@Composable
private fun MainScaffold(
    current: AppDestination,
    snackbarHostState: SnackbarHostState,
    onNavigate: (AppDestination) -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val items = listOf(
        AppDestination.Home,
        AppDestination.Gallery,
        AppDestination.Favorites,
    )
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = WarmPaper,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                items.forEach { destination ->
                    val icon = when (destination) {
                        AppDestination.Home -> Icons.Default.Home
                        AppDestination.Gallery -> Icons.Default.Collections
                        else -> Icons.Default.Favorite
                    }
                    NavigationBarItem(
                        selected = current.route == destination.route,
                        onClick = { onNavigate(destination) },
                        label = { Text(destination.label) },
                        icon = { Icon(icon, contentDescription = destination.label) },
                    )
                }
            }
        },
        content = content,
    )
}

@Composable
private fun AuthScreen(
    isLoading: Boolean,
    onContinue: (String, String, String?, Boolean) -> Unit,
) {
    var registerMode by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var repeatedPassword by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var acceptedTerms by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFF7EFE3), Color(0xFFF1E2CF))))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(shape = CircleShape, color = Color(0xFF0A315F), modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Palette, contentDescription = null, tint = Color.White, modifier = Modifier.size(27.dp)) }
            }
            Text("DailyCanvas", color = Color(0xFF0A315F), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(32.dp))
        Text(
            text = if (registerMode) "Регистрация" else "Вход",
            modifier = Modifier.align(Alignment.CenterHorizontally),
            fontFamily = FontFamily.SansSerif,
            fontSize = 31.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = if (registerMode) "Создайте личную коллекцию искусства" else "Продолжите знакомство с искусством",
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp, bottom = 26.dp),
            color = Color(0xFF6B625B),
            fontSize = 15.sp,
        )
        if (registerMode) {
            AuthField("Имя", "Введите ваше имя", name, { name = it })
            Spacer(Modifier.height(12.dp))
        }
        AuthField(if (registerMode) "Email" else "Email или телефон", "Введите email", email, { email = it })
        Spacer(Modifier.height(12.dp))
        AuthField("Пароль", if (registerMode) "Создайте пароль" else "Введите пароль", password, { password = it }, true, passwordVisible, { passwordVisible = !passwordVisible })
        if (registerMode) {
            Spacer(Modifier.height(12.dp))
            AuthField("Повторите пароль", "Повторите пароль", repeatedPassword, { repeatedPassword = it }, true, passwordVisible, { passwordVisible = !passwordVisible })
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(checked = acceptedTerms, onCheckedChange = { acceptedTerms = it })
                Text("Я согласен с условиями и политикой конфиденциальности", fontSize = 13.sp)
            }
        }
        error?.let { Text(it, color = AccentRed, fontSize = 13.sp) }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                error = when {
                    email.isBlank() || password.length < 8 -> "Введите email и пароль не короче 8 символов"
                    registerMode && name.isBlank() -> "Укажите имя"
                    registerMode && password != repeatedPassword -> "Пароли не совпадают"
                    registerMode && !acceptedTerms -> "Нужно принять условия"
                    else -> null
                }
                if (error == null) onContinue(email, password, name.takeIf { registerMode }, registerMode)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
            else Text(if (registerMode) "Зарегистрироваться" else "Войти")
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { registerMode = !registerMode }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (registerMode) "Уже есть аккаунт?  Войти" else "Нет аккаунта?  Зарегистрироваться")
        }
    }
}

@Composable
private fun AuthField(label: String, placeholder: String, value: String, onValueChange: (String) -> Unit, password: Boolean = false, visible: Boolean = false, onToggleVisible: () -> Unit = {}) {
    Text(label, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(5.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        singleLine = true,
        shape = RoundedCornerShape(13.dp),
        visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (password) ({
            TextButton(onClick = onToggleVisible, contentPadding = PaddingValues(0.dp)) {
                AppIcon(
                    resource = if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                    contentDescription = if (visible) "Скрыть пароль" else "Показать пароль",
                    modifier = Modifier.size(23.dp),
                    tint = Color(0xFF5F5B58),
                )
            }
        }) else null,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeScreen(
    modifier: Modifier = Modifier,
    state: DailyCanvasUiState,
    onOpenArtwork: (String) -> Unit,
    onFavoriteToggle: (ArtworkCard) -> Unit,
    onTagClick: (ArtworkTag) -> Unit,
    onArtistClick: (String) -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val payload = state.payload
    var descriptionExpanded by rememberSaveable { mutableStateOf(false) }
    when {
        state.isLoading -> Loader(modifier)
        payload == null -> EmptyState(modifier, "Данные пока недоступны")
        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = greetingFor(LocalTime.now()),
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 30.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF121212),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        HeaderIconButton(onClick = onOpenCalendar) {
                            CalendarIcon(modifier = Modifier.size(24.dp))
                        }
                        HeaderIconButton(onClick = onOpenSettings) {
                            SettingsIcon(modifier = Modifier.size(23.dp))
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Картина дня",
                    fontSize = 18.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF252525),
                )
            }
            item {
                val artwork = payload.artworkOfDay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.28f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onOpenArtwork(artwork.id) },
                ) {
                    AsyncImage(
                        model = artwork.imageUrl,
                        contentDescription = artwork.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Text(
                        text = formatArtworkDate(payload.date),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .background(Color(0xFF082C58), RoundedCornerShape(bottomEnd = 14.dp))
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF082C58)),
                    )
                }
            }
            item {
                val artwork = payload.artworkOfDay
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            artwork.title,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 32.sp,
                            lineHeight = 36.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            artwork.artist,
                            fontSize = 22.sp,
                            lineHeight = 28.sp,
                            color = Color(0xFF5F5B58),
                            modifier = Modifier.clickable { onArtistClick(artwork.artistId) },
                        )
                        Text(
                            text = artwork.year,
                            fontSize = 22.sp,
                            lineHeight = 28.sp,
                            color = Color(0xFF5F5B58),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .border(1.dp, Color(0xFFD8D3CC), RoundedCornerShape(29.dp))
                            .clickable { onFavoriteToggle(artwork) },
                        contentAlignment = Alignment.Center,
                    ) {
                        FavoriteIcon(
                            isFavorite = artwork.isFavorite,
                            modifier = Modifier.size(30.dp),
                            tint = if (artwork.isFavorite) AccentRed else Color(0xFF172332),
                        )
                    }
                }
            }
            item {
                val description = payload.artworkOfDay.description
                Text(
                    text = if (descriptionExpanded || description.length <= 170) description else description.take(170).trimEnd() + "…",
                    fontSize = 17.sp,
                    lineHeight = 25.sp,
                    color = Color(0xFF252525),
                )
                if (description.length > 170) {
                    TextButton(onClick = { descriptionExpanded = !descriptionExpanded }) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                            if (descriptionExpanded) "Свернуть" else "Показать полностью",
                            color = Color(0xFF082C58),
                            fontSize = 16.sp,
                            )
                            AppIcon(
                                resource = if (descriptionExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = Color(0xFF082C58),
                            )
                        }
                    }
                }
            }
            if (payload.artworkOfDay.tags.isNotEmpty()) {
                item {
                    Text("Теги", fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        payload.artworkOfDay.tags.forEach { tag ->
                            ArtworkTagChip(tag = tag, onClick = { onTagClick(tag) })
                        }
                    }
                }
            }
        }
    }
}

private fun greetingFor(time: LocalTime): String = when (time.hour) {
    in 5..11 -> "Доброе утро!"
    in 12..17 -> "Добрый день!"
    in 18..22 -> "Добрый вечер!"
    else -> "Доброй ночи!"
}

private fun formatArtworkDate(value: String): String = runCatching {
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru")))
}.getOrDefault(value)

@Composable
private fun CalendarIcon(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_calendar_custom),
        contentDescription = "Календарь",
        modifier = modifier,
        tint = Color.Unspecified,
    )
}

@Composable
private fun HeaderIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun SettingsIcon(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_settings_custom),
        contentDescription = "Настройки",
        modifier = modifier,
        tint = Color.Unspecified,
    )
}

@Composable
private fun AppIcon(
    @DrawableRes resource: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF172332),
) {
    Icon(
        painter = painterResource(resource),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

@Composable
private fun FavoriteIcon(
    isFavorite: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF172332),
) {
    AppIcon(
        resource = if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border,
        contentDescription = if (isFavorite) "Удалить из избранного" else "Добавить в избранное",
        modifier = modifier,
        tint = tint,
    )
}

@Composable
private fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onBack != null) {
            BackButton(onClick = onBack)
        }
        Text(
            title,
            modifier = Modifier.weight(1f),
            fontFamily = FontFamily.SansSerif,
            fontSize = 32.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Medium,
        )
        if (onOpenSettings != null) {
            TextButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(44.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(0.dp),
            ) {
                SettingsIcon(modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(
            resource = R.drawable.ic_arrow_back,
            contentDescription = "Назад",
            modifier = Modifier.size(28.dp),
            tint = Color(0xFF172332),
        )
    }
}

@Composable
private fun CalendarScreen(
    modifier: Modifier = Modifier,
    state: DailyCanvasUiState,
    onInitialize: () -> Unit,
    onLoadMonth: (String, String) -> Unit,
    onOpenArtwork: (String) -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val month = state.calendarMonth?.let(YearMonth::parse) ?: YearMonth.now()
    val context = LocalContext.current
    val today = LocalDate.now()
    val artworks = state.calendar.associateBy { it.date }
    val leadingEmptyCells = month.atDay(1).dayOfWeek.value - 1
    val dates = List<LocalDate?>(leadingEmptyCells) { null } +
        (1..month.lengthOfMonth()).map(month::atDay)

    LaunchedEffect(Unit) {
        onInitialize()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 22.dp),
    ) {
        ScreenHeader("Календарь", onBack = onBack, onOpenSettings = onOpenSettings)
        Spacer(Modifier.height(22.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFE0DCD6), RoundedCornerShape(28.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = {
                val target = month.minusMonths(1)
                onLoadMonth(target.atDay(1).toString(), target.atEndOfMonth().toString())
            }) {
                AppIcon(
                    resource = R.drawable.ic_chevron_left,
                    contentDescription = "Предыдущий месяц",
                    modifier = Modifier.size(28.dp),
                )
            }
            Text(
                month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("ru")))
                    .replaceFirstChar { it.titlecase(Locale.forLanguageTag("ru")) },
                fontFamily = FontFamily.SansSerif,
                fontSize = 25.sp,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(
                enabled = month < YearMonth.now(),
                onClick = {
                    val target = month.plusMonths(1)
                    onLoadMonth(target.atDay(1).toString(), target.atEndOfMonth().toString())
                },
            ) {
                AppIcon(
                    resource = R.drawable.ic_chevron_right,
                    contentDescription = "Следующий месяц",
                    modifier = Modifier.size(28.dp),
                    tint = if (month < YearMonth.now()) Color(0xFF172332) else Color.LightGray,
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp)) {
            listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach { day ->
                Text(
                    day,
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF77736F),
                    fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        if (state.isCalendarLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                gridItems(dates) { date ->
                    CalendarDay(
                        date = date,
                        today = today,
                        artwork = date?.let { artworks[it.toString()] },
                        onOpenArtwork = onOpenArtwork,
                        onMissingArtwork = {
                            Toast.makeText(
                                context,
                                "В этот день картина не публиковалась. Выберите соседнюю дату.",
                                Toast.LENGTH_LONG,
                            ).show()
                        },
                        onFutureArtwork = {
                            Toast.makeText(
                                context,
                                "Эта картина ещё не разблокирована. Возвращайтесь в этот день!",
                                Toast.LENGTH_LONG,
                            ).show()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarDay(
    date: LocalDate?,
    today: LocalDate,
    artwork: CalendarArtwork?,
    onOpenArtwork: (String) -> Unit,
    onMissingArtwork: () -> Unit,
    onFutureArtwork: () -> Unit,
) {
    if (date == null) {
        Spacer(Modifier.height(78.dp))
        return
    }
    val isFuture = date > today
    Column(
        modifier = Modifier
            .height(78.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            date.dayOfMonth.toString(),
            fontSize = 13.sp,
            color = if (isFuture) Color(0xFF9B9894) else Color(0xFF202020),
        )
        Box(
            modifier = Modifier
                .size(49.dp)
                .then(
                    when {
                        artwork != null && !isFuture -> Modifier.clickable { onOpenArtwork(artwork.artworkId) }
                        artwork == null && !isFuture -> Modifier.clickable(onClick = onMissingArtwork)
                        isFuture -> Modifier.clickable(onClick = onFutureArtwork)
                        else -> Modifier
                    },
                )
                .border(
                    width = if (date == today) 2.dp else 0.dp,
                    color = if (date == today) Color(0xFF082C58) else Color.Transparent,
                    shape = CircleShape,
                )
                .padding(if (date == today) 3.dp else 0.dp)
                .clip(CircleShape)
                .background(if (isFuture) Color(0xFFE3E1DD) else Color(0xFFF0EEEA)),
            contentAlignment = Alignment.Center,
        ) {
            if (artwork != null) {
                AsyncImage(
                    model = artwork.imageUrl,
                    contentDescription = "Картина за ${date.dayOfMonth} число",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    alpha = if (isFuture) 0.28f else 1f,
                )
            }
            if (isFuture) {
                AppIcon(
                    resource = R.drawable.ic_lock,
                    contentDescription = "Будущая дата",
                    modifier = Modifier.size(17.dp),
                    tint = Color(0xFF77736F),
                )
            }
        }
    }
}

@Composable
private fun GalleryScreen(
    modifier: Modifier = Modifier,
    state: DailyCanvasUiState,
    tagFilter: String?,
    artistFilter: String?,
    onClearFilter: () -> Unit,
    onOpenArtwork: (String) -> Unit,
    onFavoriteToggle: (ArtworkCard) -> Unit,
    onOpenSettings: () -> Unit,
    gridMode: Boolean,
    onGridModeChanged: (Boolean) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("Все") }
    var selectedAuthorId by rememberSaveable { mutableStateOf<String?>(null) }
    val allItems = state.payload?.gallery.orEmpty()
    val authors = allItems
        .distinctBy { it.artistId }
        .sortedBy { it.artist }
    val effectiveArtistFilter = artistFilter ?: selectedAuthorId
    val items = allItems.filter { artwork ->
        (tagFilter == null || artwork.tags.any { it.id == tagFilter }) &&
            (effectiveArtistFilter == null || artwork.artistId == effectiveArtistFilter) &&
            (query.isBlank() || listOf(artwork.title, artwork.artist, artwork.year).any { it.contains(query, ignoreCase = true) } || artwork.tags.any { it.name.contains(query, ignoreCase = true) }) &&
            categoryMatches(artwork, selectedCategory)
    }
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GalleryHeader("Галерея", gridMode, { onGridModeChanged(!gridMode) }, onOpenSettings)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Поиск картин, авторов, тегов…") },
            trailingIcon = {
                AppIcon(
                    resource = R.drawable.ic_search,
                    contentDescription = "Поиск",
                    modifier = Modifier.size(23.dp),
                    tint = Color(0xFF5F5B58),
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(26.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("Все", "Автор", "Жанр", "Стиль", "Страна", "Век")) { category ->
                Text(
                    category,
                    color = if (selectedCategory == category) Color.White else Color(0xFF262626),
                    modifier = Modifier
                        .background(if (selectedCategory == category) Color(0xFF082C58) else Color(0xFFF0EEEA), RoundedCornerShape(22.dp))
                        .clickable {
                            selectedCategory = category
                            if (category != "Автор") selectedAuthorId = null
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
        if (selectedCategory == "Автор") {
            Text(
                text = "Выберите автора",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF5F5B58),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChoiceChip(
                        text = "Все авторы",
                        selected = effectiveArtistFilter == null,
                        onClick = {
                            selectedAuthorId = null
                            onClearFilter()
                        },
                    )
                }
                items(authors, key = { it.artistId }) { artwork ->
                    FilterChoiceChip(
                        text = artwork.artist,
                        selected = effectiveArtistFilter == artwork.artistId,
                        onClick = {
                            onClearFilter()
                            selectedAuthorId = artwork.artistId
                        },
                    )
                }
            }
        }
        if (tagFilter != null || effectiveArtistFilter != null) {
            val activeFilterName = effectiveArtistFilter?.let { id ->
                allItems.firstOrNull { it.artistId == id }?.artist
            } ?: tagFilter?.let { id ->
                allItems.asSequence().flatMap { it.tags.asSequence() }.firstOrNull { it.id == id }?.name
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = activeFilterName?.let { "Фильтр: $it" } ?: "Включён фильтр",
                    color = Color(0xFF082C58),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    selectedAuthorId = null
                    onClearFilter()
                }) { Text("Сбросить") }
            }
        }
        if (gridMode) {
            if (items.isEmpty()) GalleryEmptyState(if (query.isBlank()) "В этой категории пока нет картин" else "Ничего не нашлось") else LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                gridItems(items) { artwork -> ArtworkGridCard(artwork, { onOpenArtwork(artwork.id) }, { onFavoriteToggle(artwork) }) }
            }
        } else {
            if (items.isEmpty()) GalleryEmptyState(if (query.isBlank()) "В этой категории пока нет картин" else "Ничего не нашлось") else LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items) { artwork ->
                    ArtworkRow(artwork, { onOpenArtwork(artwork.id) }, { onFavoriteToggle(artwork) })
                }
            }
        }
    }
}

@Composable
private fun FavoritesScreen(
    modifier: Modifier = Modifier,
    state: DailyCanvasUiState,
    onOpenArtwork: (String) -> Unit,
    onFavoriteToggle: (ArtworkCard) -> Unit,
    onOpenSettings: () -> Unit,
    gridMode: Boolean,
    onGridModeChanged: (Boolean) -> Unit,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    var sortMode by rememberSaveable { mutableStateOf("Недавно добавленные") }
    val source = state.payload?.favorites.orEmpty()
    val items = when (sortMode) {
        "По названию" -> source.sortedBy { it.title }
        "По автору" -> source.sortedBy { it.artist }
        "По году" -> source.sortedByDescending { it.year.toIntOrNull() ?: 0 }
        else -> source
    }
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GalleryHeader("Избранное", gridMode, { onGridModeChanged(!gridMode) }, onOpenSettings)
        Box {
            OutlinedButton(onClick = { sortMenuOpen = true }, shape = RoundedCornerShape(18.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(sortMode)
                    AppIcon(
                        resource = R.drawable.ic_expand_more,
                        contentDescription = "Выбрать сортировку",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                listOf("Недавно добавленные", "По названию", "По автору", "По году").forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = { sortMode = option; sortMenuOpen = false })
                }
            }
        }
        if (gridMode) {
            if (items.isEmpty()) GalleryEmptyState("В избранном пока пусто\nНажмите на сердечко у понравившейся картины") else LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                gridItems(items) { artwork -> ArtworkGridCard(artwork, { onOpenArtwork(artwork.id) }, { onFavoriteToggle(artwork) }) }
            }
        } else {
            if (items.isEmpty()) GalleryEmptyState("В избранном пока пусто\nНажмите на сердечко у понравившейся картины") else LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items) { artwork -> ArtworkRow(artwork, { onOpenArtwork(artwork.id) }, { onFavoriteToggle(artwork) }) }
            }
        }
    }
}

private fun categoryMatches(artwork: ArtworkCard, category: String): Boolean = when (category) {
    "Жанр" -> artwork.tags.any { it.type == "genre" }
    "Стиль" -> artwork.tags.any { it.type == "style" }
    "Страна" -> artwork.tags.any { it.type == "collection" }
    "Век" -> artwork.tags.any { it.type == "period" }
    else -> true
}

@Composable
private fun FilterChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        color = if (selected) Color.White else Color(0xFF243142),
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .background(
                color = if (selected) Color(0xFF082C58) else Color(0xFFF0EEEB),
                shape = RoundedCornerShape(50),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun ArtworkTagChip(
    tag: ArtworkTag,
    onClick: () -> Unit,
) {
    Text(
        text = tag.name,
        color = Color(0xFF243142),
        fontSize = 16.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .background(Color(0xFFE3DED7), RoundedCornerShape(50))
            .border(1.dp, Color(0xFFCFC8BF), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun GalleryHeader(title: String, gridMode: Boolean, onToggleGrid: () -> Unit, onOpenSettings: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.size(44.dp))
        Text(title, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontFamily = FontFamily.SansSerif, fontSize = 30.sp, fontWeight = FontWeight.Medium)
        TextButton(onClick = onToggleGrid, modifier = Modifier.size(44.dp), contentPadding = PaddingValues(0.dp)) {
            AppIcon(
                resource = if (gridMode) R.drawable.ic_view_list else R.drawable.ic_grid_view,
                contentDescription = if (gridMode) "Показать списком" else "Показать сеткой",
                modifier = Modifier.size(24.dp),
            )
        }
        TextButton(
            onClick = onOpenSettings,
            modifier = Modifier.size(40.dp),
            contentPadding = PaddingValues(0.dp),
            shape = RoundedCornerShape(0.dp),
        ) {
            SettingsIcon(modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun ArtworkGridCard(artwork: ArtworkCard, onOpen: () -> Unit, onFavoriteToggle: () -> Unit) {
    Card(
        onClick = onOpen,
        modifier = Modifier.height(300.dp),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = artwork.imageUrl,
                contentDescription = artwork.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(174.dp),
            )
            Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        artwork.title,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 18.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        softWrap = true,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Text(
                        artwork.artist,
                        modifier = Modifier.padding(end = 44.dp),
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Text(
                        artwork.year,
                        modifier = Modifier.padding(end = 44.dp),
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                    )
                }
                TextButton(
                    onClick = onFavoriteToggle,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(36.dp).align(Alignment.BottomEnd),
                ) {
                    FavoriteIcon(
                        isFavorite = artwork.isFavorite,
                        modifier = Modifier.size(25.dp),
                        tint = if (artwork.isFavorite) AccentRed else Color(0xFF777777),
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.GalleryEmptyState(message: String) {
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppIcon(
            resource = R.drawable.ic_empty_gallery,
            contentDescription = null,
            modifier = Modifier.size(52.dp),
            tint = Color(0xFFB7B0A8),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            message,
            color = Color(0xFF68635F),
            fontSize = 16.sp,
            lineHeight = 23.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier = Modifier,
    settings: SettingsPayload?,
    profileEmail: String,
    onNotificationsChanged: (Boolean, String) -> Unit,
    onCollectionsChanged: (List<String>) -> Unit,
    onPasswordChange: (String, String) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    val current = settings ?: return EmptyState(modifier, "Настройки пока не загружены")
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var showPasswordDialog by rememberSaveable { mutableStateOf(false) }
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader("Настройки", onBack = onBack) }
        item {
            Text("Профиль", fontFamily = FontFamily.SansSerif, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            SettingsGroup {
                SettingsActionRow("Email", value = profileEmail)
                HorizontalRule()
                SettingsActionRow("Сменить пароль", onClick = { showPasswordDialog = true })
                HorizontalRule()
                SettingsActionRow("Выйти из аккаунта", onClick = { confirmLogout = true })
                HorizontalRule()
                SettingsActionRow("Удалить аккаунт", onClick = { confirmDelete = true })
            }
        }
        item { HorizontalRule() }
        item {
            Text("Тематика", fontFamily = FontFamily.SansSerif, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Выберите, картины каких коллекций вы хотите видеть каждый день", color = Color(0xFF68635F), fontSize = 16.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(10.dp))
            CollectionSetting(
                selected = current.selectedCollections,
                onSelected = onCollectionsChanged,
            )
        }
        item { HorizontalRule() }
        item {
            Text("Напоминания", fontFamily = FontFamily.SansSerif, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Показывать напоминание\nо новой картине дня", modifier = Modifier.weight(1f), color = Color(0xFF5F5B58), fontSize = 16.sp, lineHeight = 22.sp)
                Switch(checked = current.notificationsEnabled, onCheckedChange = { onNotificationsChanged(it, current.notificationTime) })
            }
        }
        item {
            Box(modifier = Modifier.graphicsLayer { alpha = if (current.notificationsEnabled) 1f else 0.42f }) {
                SettingCard(
                    title = "Время напоминания",
                    value = current.notificationTime,
                    onClick = if (current.notificationsEnabled) ({ showTimePicker = true }) else null,
                )
            }
        }
        item { HorizontalRule() }
        item {
            Text("О приложении", fontFamily = FontFamily.SansSerif, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            SettingsGroup {
                SettingsActionRow("Версия", value = "1.0.0")
                HorizontalRule()
                SettingsActionRow("Служба поддержки", onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:support@dailycanvas.app"))
                    runCatching { context.startActivity(intent) }
                        .onFailure { Toast.makeText(context, "support@dailycanvas.app", Toast.LENGTH_LONG).show() }
                })
            }
        }
    }
    if (showTimePicker) {
        ReminderTimeDialog(
            initialTime = current.notificationTime,
            onDismiss = { showTimePicker = false },
            onConfirm = { time ->
                showTimePicker = false
                onNotificationsChanged(current.notificationsEnabled, time)
            },
        )
    }
    if (showPasswordDialog) PasswordChangeDialog(
        onDismiss = { showPasswordDialog = false },
        onSubmit = { currentPassword, newPassword ->
            showPasswordDialog = false
            onPasswordChange(currentPassword, newPassword)
        },
    )
    if (confirmLogout) ConfirmActionDialog("Выйти из аккаунта?", "На этом устройстве потребуется повторный вход.", "Выйти", { confirmLogout = false }, onLogout)
    if (confirmDelete) ConfirmActionDialog("Удалить аккаунт?", "Профиль, настройки и избранное будут безвозвратно удалены из базы данных.", "Удалить", { confirmDelete = false }, onDeleteAccount)
}

@Composable
private fun PasswordChangeDialog(onDismiss: () -> Unit, onSubmit: (String, String) -> Unit) {
    var oldPassword by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var repeated by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Смена пароля") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(oldPassword, { oldPassword = it }, label = { Text("Старый пароль") }, visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(newPassword, { newPassword = it }, label = { Text("Новый пароль") }, visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(repeated, { repeated = it }, label = { Text("Повторите пароль") }, visualTransformation = PasswordVisualTransformation())
            message?.let { Text(it, color = AccentRed, fontSize = 13.sp) }
        } },
        confirmButton = { TextButton(onClick = {
            message = when { oldPassword.isBlank() -> "Введите старый пароль"; newPassword.length < 8 -> "Новый пароль должен содержать не менее 8 символов"; newPassword != repeated -> "Пароли не совпадают"; else -> null }
            if (message == null) onSubmit(oldPassword, newPassword)
        }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun ConfirmActionDialog(title: String, message: String, action: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) }, confirmButton = { TextButton(onClick = onConfirm) { Text(action, color = AccentRed) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailScreen(
    artwork: ArtworkCard?,
    onBack: () -> Unit,
    onFavoriteToggle: (ArtworkCard) -> Unit,
    onTagClick: (ArtworkTag) -> Unit,
    onArtistClick: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    if (artwork == null) {
        EmptyState(Modifier.fillMaxSize(), "Картина не выбрана")
        return
    }

    var fullScreenImage by rememberSaveable { mutableStateOf(false) }
    var showShareMenu by rememberSaveable { mutableStateOf(false) }
    var descriptionExpanded by rememberSaveable(artwork.id) { mutableStateOf(false) }
    var descriptionHasOverflow by remember(artwork.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BackButton(onClick = onBack)
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(44.dp),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(0.dp),
                ) {
                    SettingsIcon(modifier = Modifier.size(27.dp))
                }
                TextButton(onClick = { showShareMenu = true }, modifier = Modifier.size(44.dp), contentPadding = PaddingValues(0.dp)) {
                    AppIcon(
                        resource = R.drawable.ic_share,
                        contentDescription = "Поделиться или скачать",
                        modifier = Modifier.size(25.dp),
                    )
                }
            }
        }
        item {
            AsyncImage(
                model = artwork.imageUrl,
                contentDescription = artwork.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.45f)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { fullScreenImage = true },
            )
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(artwork.title, fontFamily = FontFamily.SansSerif, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Medium)
                    Text(
                        artwork.artist,
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        color = Color(0xFF4E4A47),
                        modifier = Modifier.clickable { onArtistClick(artwork.artistId) },
                    )
                    Text(
                        text = artwork.year,
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        color = Color(0xFF5F5B58),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(if (artwork.isFavorite) Color(0xFFE33F45) else Color(0xFFE9E5E0))
                        .clickable { onFavoriteToggle(artwork) },
                    contentAlignment = Alignment.Center,
                ) {
                    FavoriteIcon(
                        isFavorite = artwork.isFavorite,
                        modifier = Modifier.size(29.dp),
                        tint = if (artwork.isFavorite) Color.White else Color(0xFF172332),
                    )
                }
            }
        }
        item {
            Text(
                text = artwork.description.ifBlank { "Описание будет добавлено." },
                fontSize = 18.sp,
                lineHeight = 27.sp,
                maxLines = if (descriptionExpanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { result ->
                    if (!descriptionExpanded) descriptionHasOverflow = result.hasVisualOverflow
                },
            )
            if (descriptionHasOverflow || descriptionExpanded) {
                Row(
                    modifier = Modifier
                        .clickable { descriptionExpanded = !descriptionExpanded }
                        .padding(top = 12.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (descriptionExpanded) "Свернуть" else "Показать полностью",
                        color = Color(0xFF082C58),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    AppIcon(
                        resource = if (descriptionExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFF082C58),
                    )
                }
            }
        }
        if (artwork.facts.isNotEmpty()) {
            item {
                Text("Интересные факты", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                artwork.facts.forEach { fact -> Text("•  $fact", fontSize = 16.sp, lineHeight = 23.sp) }
            }
        }
        if (artwork.tags.isNotEmpty()) {
            item {
                Text("Теги", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    artwork.tags.forEach { tag ->
                        ArtworkTagChip(tag = tag, onClick = { onTagClick(tag) })
                    }
                }
            }
        }
    }
    if (fullScreenImage) {
        ZoomableArtworkDialog(
            artwork = artwork,
            onDismiss = { fullScreenImage = false },
        )
    }
    if (showShareMenu) {
        ShareDownloadDialog(
            onDismiss = { showShareMenu = false },
            onShare = {
                showShareMenu = false
                shareArtwork(context, artwork)
            },
            onDownload = { format ->
                showShareMenu = false
                scope.launch {
                    val saved = saveArtwork(context, artwork, format)
                    Toast.makeText(context, if (saved) "Картина сохранена" else "Не удалось сохранить", Toast.LENGTH_LONG).show()
                }
            },
        )
    }
}

@Composable
private fun ArtworkHeroCard(
    artwork: ArtworkCard,
    onOpen: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onArtistClick: (() -> Unit)? = null,
    onTagClick: ((ArtworkTag) -> Unit)? = null,
) {
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(
                model = artwork.imageUrl,
                contentDescription = artwork.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(WarmPaper),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(artwork.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${artwork.artist} • ${artwork.year}",
                        color = Color(0xFF665D56),
                        modifier = if (onArtistClick != null) Modifier.clickable(onClick = onArtistClick) else Modifier,
                    )
                }
                Button(onClick = onFavoriteToggle) {
                    FavoriteIcon(
                        isFavorite = artwork.isFavorite,
                        modifier = Modifier.size(22.dp),
                        tint = Color.White,
                    )
                }
            }
            if (artwork.tags.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(artwork.tags) { tag ->
                        if (onTagClick != null) {
                            TextButton(onClick = { onTagClick(tag) }) { Text(tag.name) }
                        } else {
                            Text(tag.name, color = AccentRed)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LazyItemScope.ArtworkMiniCard(
    artwork: ArtworkCard,
    onOpen: () -> Unit,
) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillParentMaxWidth(0.8f),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AsyncImage(
                model = artwork.imageUrl,
                contentDescription = artwork.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(WarmPaper),
            )
            Text(artwork.title, fontWeight = FontWeight.SemiBold)
            Text("${artwork.artist} • ${artwork.year}", color = Color(0xFF665D56))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtworkRow(
    artwork: ArtworkCard,
    onOpen: () -> Unit,
    onFavoriteToggle: (() -> Unit)?,
) {
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = artwork.imageUrl,
                contentDescription = artwork.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .height(84.dp)
                    .weight(0.28f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(WarmPaper),
            )
            Column(modifier = Modifier.weight(0.56f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(artwork.title, fontWeight = FontWeight.SemiBold)
                Text("${artwork.artist} • ${artwork.year}", color = Color(0xFF665D56))
                if (artwork.tags.isNotEmpty()) {
                    Text(artwork.tags.take(2).joinToString(" · ") { it.name }, color = AccentRed)
                }
            }
            if (onFavoriteToggle != null) {
                Button(onClick = onFavoriteToggle, modifier = Modifier.weight(0.16f)) {
                    FavoriteIcon(
                        isFavorite = artwork.isFavorite,
                        modifier = Modifier.size(21.dp),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionSetting(
    selected: List<String>,
    onSelected: (List<String>) -> Unit,
) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            CollectionToggleRow("Русская живопись", "russian", selected, onSelected)
            HorizontalRule()
            CollectionToggleRow("Зарубежная живопись", "world", selected, onSelected)
        }
    }
}

@Composable
private fun CollectionToggleRow(
    label: String,
    value: String,
    selected: List<String>,
    onSelected: (List<String>) -> Unit,
) {
    val isSelected = value in selected
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val updated = if (isSelected) selected - value else selected + value
                if (updated.isNotEmpty()) onSelected(updated.distinct())
            }
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 17.sp)
        Box(
            modifier = Modifier.size(30.dp).clip(CircleShape).background(if (isSelected) Color(0xFF082C58) else Color(0xFFE6E2DC)),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                AppIcon(
                    resource = R.drawable.ic_check,
                    contentDescription = "Выбрано",
                    modifier = Modifier.size(19.dp),
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun HorizontalRule() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE5E0DA)))
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun SettingsActionRow(
    label: String,
    value: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 16.sp)
        if (onClick != null) {
            AppIcon(
                resource = R.drawable.ic_chevron_right,
                contentDescription = "Открыть",
                modifier = Modifier.size(22.dp),
                tint = Color(0xFF55504C),
            )
        } else if (value != null) {
            Text(value, color = Color(0xFF55504C), fontSize = 16.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(
    initialTime: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val parts = initialTime.split(":")
    val pickerState = rememberTimePickerState(
        initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 9,
        initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Время напоминания") },
        text = { TimePicker(state = pickerState) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm("%02d:%02d".format(pickerState.hour, pickerState.minute))
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun ZoomableArtworkDialog(
    artwork: ArtworkCard,
    onDismiss: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var showHint by rememberSaveable { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.88f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = artwork.imageUrl,
                contentDescription = artwork.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            showHint = false
                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                            val maxOffsetX = size.width * (newScale - 1f) / 2f
                            val maxOffsetY = size.height * (newScale - 1f) / 2f
                            scale = newScale
                            if (newScale == 1f) {
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { tap ->
                                showHint = false
                                if (scale > 1f) {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                } else {
                                    scale = 2.5f
                                    offsetX = (size.width / 2f - tap.x) * (scale - 1f)
                                    offsetY = (size.height / 2f - tap.y) * (scale - 1f)
                                }
                            },
                        )
                    },
            )
            if (showHint) {
                Text(
                    "Дважды нажмите или разведите пальцы",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 38.dp)
                        .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }
    }
}

private enum class ArtworkFormat(val extension: String, val mimeType: String) {
    JPG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    WEBP("webp", "image/webp"),
}

@Composable
private fun ShareDownloadDialog(
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onDownload: (ArtworkFormat) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = {
            Column {
                Text("Картина с собой", fontFamily = FontFamily.SansSerif, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("Поделитесь находкой или сохраните её в галерею", color = Color(0xFF68635F), fontSize = 14.sp, lineHeight = 19.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Card(
                    onClick = onShare,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF082C58)),
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                            AppIcon(
                                resource = R.drawable.ic_share,
                                contentDescription = null,
                                modifier = Modifier.size(23.dp),
                                tint = Color.White,
                            )
                        }
                        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                            Text("Поделиться", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("Выбрать соцсеть или мессенджер", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                        }
                        AppIcon(
                            resource = R.drawable.ic_chevron_right,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = Color.White,
                        )
                    }
                }
                Text("Скачать", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ArtworkFormat.entries.forEach { format ->
                        Card(
                            onClick = { onDownload(format) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1EFEB)),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                AppIcon(
                                    resource = R.drawable.ic_download,
                                    contentDescription = "Скачать ${format.name}",
                                    modifier = Modifier.size(23.dp),
                                    tint = Color(0xFF082C58),
                                )
                                Text(format.name, color = Color(0xFF172332), fontWeight = FontWeight.Bold)
                                Text(if (format == ArtworkFormat.JPG) "компактно" else if (format == ArtworkFormat.PNG) "качество" else "современно", color = Color(0xFF77716C), fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Не сейчас", color = Color(0xFF5F5B58)) } },
    )
}

private fun shareArtwork(context: Context, artwork: ArtworkCard) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, artwork.title)
        putExtra(
            Intent.EXTRA_TEXT,
            "${artwork.title} — ${artwork.artist}, ${artwork.year}\n${artwork.imageUrl}",
        )
    }
    context.startActivity(Intent.createChooser(shareIntent, "Поделиться картиной"))
}

private suspend fun saveArtwork(
    context: Context,
    artwork: ArtworkCard,
    format: ArtworkFormat,
): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val request = ImageRequest.Builder(context)
            .data(artwork.imageUrl)
            .allowHardware(false)
            .build()
        val drawable = ImageLoader(context).execute(request).drawable as? BitmapDrawable
            ?: error("Image is not a bitmap")
        val bitmap = drawable.bitmap
        val fileName = artwork.title
            .lowercase(Locale.getDefault())
            .replace(Regex("[^a-zа-яё0-9]+"), "-")
            .trim('-') + ".${format.extension}"
        val compressFormat = when (format) {
            ArtworkFormat.JPG -> Bitmap.CompressFormat.JPEG
            ArtworkFormat.PNG -> Bitmap.CompressFormat.PNG
            ArtworkFormat.WEBP -> if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Daily Canvas")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Cannot create media file")
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                check(bitmap.compress(compressFormat, 95, stream))
            } ?: error("Cannot open media file")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        } else {
            val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Daily Canvas").apply { mkdirs() }
            FileOutputStream(File(directory, fileName)).use { stream ->
                check(bitmap.compress(compressFormat, 95, stream))
            }
        }
    }.isSuccess
}

@Composable
private fun SettingCard(title: String, value: String, onClick: (() -> Unit)? = null) {
    if (onClick == null) {
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            SettingCardContent(title, value)
        }
    } else {
        Card(onClick = onClick, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            SettingCardContent(title, value)
        }
    }
}

@Composable
private fun SettingCardContent(title: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(value, color = Color(0xFF665D56))
    }
}

@Composable
private fun SettingToggleCard(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun Loader(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier, text: String) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text)
    }
}
