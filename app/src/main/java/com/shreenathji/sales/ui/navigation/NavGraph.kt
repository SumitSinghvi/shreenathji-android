package com.shreenathji.sales.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shreenathji.sales.data.local.auth.AuthManager
import com.shreenathji.sales.data.local.auth.LicenseSessionGuard
import com.shreenathji.sales.data.remote.SupabaseClient
import com.shreenathji.sales.data.repository.SalesRepository
import com.shreenathji.sales.ui.screens.DesignGalleryScreen
import com.shreenathji.sales.ui.screens.LicenseGateScreen
import com.shreenathji.sales.ui.screens.OrderFormScreen
import com.shreenathji.sales.ui.screens.OrderHistoryScreen
import com.shreenathji.sales.ui.screens.PartyListScreen
import com.shreenathji.sales.ui.theme.*
import com.shreenathji.sales.ui.viewmodels.DesignGalleryViewModel
import com.shreenathji.sales.ui.viewmodels.DesignGalleryViewModelFactory
import com.shreenathji.sales.ui.viewmodels.OrderFormViewModel
import com.shreenathji.sales.ui.viewmodels.OrderFormViewModelFactory
import com.shreenathji.sales.ui.viewmodels.OrderHistoryViewModel
import com.shreenathji.sales.ui.viewmodels.OrderHistoryViewModelFactory
import com.shreenathji.sales.ui.viewmodels.PartyListViewModel
import com.shreenathji.sales.ui.viewmodels.PartyListViewModelFactory

@Composable
fun AppNavigation(
    repository: SalesRepository,
    authManager: AuthManager,
    supabaseClient: SupabaseClient,
    licenseSessionGuard: LicenseSessionGuard,
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val bottomNavItems = listOf(
        Screen.PartyList,
        Screen.OrderForm,
        Screen.Designs,
        Screen.Orders
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isLicenseGate = currentRoute == "license_gate"

    val startDestination = if (authManager.isLicensed()) Screen.PartyList.route else "license_gate"

    LaunchedEffect(licenseSessionGuard) {
        licenseSessionGuard.sessionInvalidated.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            supabaseClient.setAuthToken(null)
            navController.navigate("license_gate") {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    Scaffold(
        containerColor = Slate50,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!isLicenseGate) {
                ModernFullWidthBottomNav(
                    items = bottomNavItems,
                    currentRoute = currentRoute,
                    onNavigate = { screen ->
                        val route = when (screen) {
                            is Screen.Designs -> Screen.Designs.createRoute(returnToOrder = false)
                            is Screen.OrderForm -> Screen.OrderForm.createRoute()
                            else -> screen.route
                        }
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Screen 0: Hardware-Locked License Activation Screen
            composable("license_gate") {
                LicenseGateScreen(
                    authManager = authManager,
                    supabaseClient = supabaseClient,
                    onLicenseActivated = {
                        repository.syncWithCloud()
                        navController.navigate(Screen.PartyList.route) {
                            popUpTo("license_gate") { inclusive = true }
                        }
                    }
                )
            }

            // Screen 1: Parties & Beat
            composable(Screen.PartyList.route) {
                val viewModel: PartyListViewModel = viewModel(
                    factory = PartyListViewModelFactory(repository, authManager)
                )
                PartyListScreen(
                    viewModel = viewModel,
                    onBookOrderForParty = { partyId ->
                        navController.navigate(Screen.OrderForm.createRoute(partyId)) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                        }
                    }
                )
            }

            // Screen 2: Book Order
            composable(
                route = Screen.OrderForm.route,
                arguments = listOf(
                    navArgument("partyId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val partyId = backStackEntry.arguments?.getString("partyId")
                val viewModel: OrderFormViewModel = viewModel(
                    factory = OrderFormViewModelFactory(repository, authManager, partyId)
                )
                // Consume designs selected from Design Gallery when returning
                LaunchedEffect(currentRoute) {
                    viewModel.consumePendingDesigns()
                }
                OrderFormScreen(
                    viewModel = viewModel,
                    onOrderSubmitted = {
                        navController.navigate(Screen.Orders.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                        }
                    },
                    onPickFromDesigns = {
                        navController.navigate(Screen.Designs.createRoute(returnToOrder = true))
                    }
                )
            }

            // Screen 2b: Design Gallery
            composable(
                route = Screen.Designs.route,
                arguments = listOf(
                    navArgument("returnToOrder") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { backStackEntry ->
                val returnToOrder = backStackEntry.arguments?.getBoolean("returnToOrder") ?: false
                val viewModel: DesignGalleryViewModel = viewModel(
                    factory = DesignGalleryViewModelFactory(repository)
                )
                DesignGalleryScreen(
                    viewModel = viewModel,
                    returnToOrderForm = returnToOrder,
                    onAddToOrder = {
                        if (returnToOrder) {
                            navController.popBackStack()
                        } else {
                            navController.navigate(Screen.OrderForm.createRoute()) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }

            // Screen 3: Orders (formerly History)
            composable(Screen.Orders.route) {
                val viewModel: OrderHistoryViewModel = viewModel(
                    factory = OrderHistoryViewModelFactory(repository, authManager)
                )
                OrderHistoryScreen(
                    viewModel = viewModel,
                    onLogout = {
                        authManager.clearLicense()
                        supabaseClient.setAuthToken(null)
                        navController.navigate("license_gate") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}

/**
 * Clean Full-Width Modern Bottom Navigation Bar with active indicator pills and smooth transitions
 */
@Composable
fun ModernFullWidthBottomNav(
    items: List<Screen>,
    currentRoute: String?,
    onNavigate: (Screen) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ShadcnWhite,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, Slate200.copy(alpha = 0.8f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { screen ->
                val isSelected = when (screen) {
                    is Screen.PartyList -> currentRoute == Screen.PartyList.route
                    is Screen.OrderForm -> currentRoute?.startsWith("order_form") == true
                    is Screen.Designs -> currentRoute?.startsWith("design_gallery") == true
                    is Screen.Orders -> currentRoute == Screen.Orders.route
                    else -> false
                }

                val pillBgColor by animateColorAsState(
                    targetValue = if (isSelected) BrandOrangeLight else Color.Transparent,
                    animationSpec = tween(durationMillis = 200),
                    label = "nav_pill_bg"
                )
                val iconColor by animateColorAsState(
                    targetValue = if (isSelected) BrandOrange else Slate400,
                    animationSpec = tween(durationMillis = 200),
                    label = "nav_icon_color"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) Slate900 else Slate500,
                    animationSpec = tween(durationMillis = 200),
                    label = "nav_text_color"
                )
                val pillWidth by animateDpAsState(
                    targetValue = if (isSelected) 56.dp else 40.dp,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
                    label = "nav_pill_width"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = androidx.compose.material.ripple.rememberRipple(bounded = true, color = BrandOrange.copy(alpha = 0.15f))
                        ) {
                            onNavigate(screen)
                        }
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Animated active pill container around icon
                        Box(
                            modifier = Modifier
                                .width(pillWidth)
                                .height(30.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(pillBgColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isSelected) screen.icon else screen.unselectedIcon,
                                contentDescription = screen.title,
                                tint = iconColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = screen.title,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = textColor,
                            maxLines = 1
                        )

                        Spacer(modifier = Modifier.height(3.dp))
                    }
                }
            }
        }
    }
}

