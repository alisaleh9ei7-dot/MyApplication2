package com.example.myapplication

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.ui.PharmacyViewModel
import com.example.myapplication.ui.PharmacyViewModelFactory
import com.example.myapplication.ui.screens.*
import com.example.myapplication.ui.theme.MyApplication2Theme
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

// 👇 استيرادات جديدة لإجبار الاتجاه العربي
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dailyWorkRequest = PeriodicWorkRequestBuilder<NotificationWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DailyPharmacyNotification",
            ExistingPeriodicWorkPolicy.KEEP,
            dailyWorkRequest
        )

        val database = AppDatabase.getDatabase(applicationContext)

        val viewModel: PharmacyViewModel by viewModels {
            PharmacyViewModelFactory(
                database.itemDao(),
                database.batchDao(),
                database.saleDao(),
                database.stockMovementDao(),
                database.expenseDao(),
                database.supplierLedgerDao(),
                database.settingDao(),
                database.dashboardDao(),
                database.companyDao(), // 👈 يجب أن يكون هنا (قبل reportDao)
                database.reportDao(),
                database
            )
        }

        setContent {
            // 👇 هذا هو السطر السحري الذي يعيد الواجهة للعربية (من اليمين لليسار)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MyApplication2Theme {
                    AppMainShell(viewModel)
                }
            }
        }
    }
}

data class NavItem(val title: String, val icon: ImageVector, val route: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppMainShell(viewModel: PharmacyViewModel) {
    var currentScreen by remember { mutableStateOf("dashboard") }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val bottomNavItems = listOf(
        NavItem("الرئيسية", Icons.Default.Home, "dashboard"),
        NavItem("المبيعات", Icons.Default.ShoppingCart, "sales"),
        NavItem("المشتريات", Icons.Default.ShoppingCart, "purchases"),
        NavItem("المصاريف", Icons.Default.Info, "expenses"),
        NavItem("المزيد", Icons.Default.Menu, "more")
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("My Pharmacy", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("إدارة المبيعات والمخزون", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(modifier = Modifier.height(8.dp))

                Text("الرئيسية", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DrawerItem("لوحة التحكم", Icons.Default.Home, currentScreen == "dashboard") { currentScreen = "dashboard"; scope.launch { drawerState.close() } }
                DrawerItem("المبيعات", Icons.Default.ShoppingCart, currentScreen == "sales") { currentScreen = "sales"; scope.launch { drawerState.close() } }
                DrawerItem("سجل المبيعات", Icons.Default.List, currentScreen == "sales_log") { currentScreen = "sales_log"; scope.launch { drawerState.close() } }

                Text("الإدارة", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DrawerItem("البحث عن صنف", Icons.Default.Search, currentScreen == "search") { currentScreen = "search"; scope.launch { drawerState.close() } }
                DrawerItem("المخزون", Icons.Default.List, currentScreen == "inventory") { currentScreen = "inventory"; scope.launch { drawerState.close() } }
                DrawerItem("المشتريات", Icons.Default.ShoppingCart, currentScreen == "purchases") { currentScreen = "purchases"; scope.launch { drawerState.close() } }
                DrawerItem("الموردون", Icons.Default.Person, currentScreen == "suppliers") { currentScreen = "suppliers"; scope.launch { drawerState.close() } }
                DrawerItem("التقارير", Icons.Default.Info, currentScreen == "reports") { currentScreen = "reports"; scope.launch { drawerState.close() } }
                DrawerItem("المصاريف", Icons.Default.Info, currentScreen == "expenses") { currentScreen = "expenses"; scope.launch { drawerState.close() } }
                DrawerItem("الإعدادات", Icons.Default.Settings, currentScreen == "settings") { currentScreen = "settings"; scope.launch { drawerState.close() } }
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background, // استخدمنا لون الخلفية من الثيم
            topBar = { },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.title) },
                            label = { Text(item.title, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            selected = currentScreen == item.route,
                            onClick = {
                                if (item.route == "more") scope.launch { drawerState.open() }
                                else currentScreen = item.route
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), // خففنا لون المؤشر
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (currentScreen) {
                    "dashboard" -> DashboardScreen(viewModel = viewModel, onNavigate = { route -> currentScreen = route })
                    "sales" -> SalesScreen(viewModel = viewModel)
                    "purchases" -> PurchasesScreen(viewModel = viewModel)
                    "expenses" -> ExpensesScreen(viewModel = viewModel)
                    "inventory" -> InventoryScreen(viewModel = viewModel)
                    "shortages" -> ShortagesScreen(viewModel = viewModel, onBack = { currentScreen = "dashboard" })
                    "sales_log" -> SalesHistoryScreen(viewModel = viewModel)
                    "suppliers" -> SuppliersScreen(viewModel = viewModel)
                    "reports" -> ReportsScreen(viewModel = viewModel)
                    "settings" -> SettingsScreen(viewModel = viewModel)
                    else -> PlaceholderScreen("شاشة أخرى")
                }
            }
        }
    }
}

@Composable
fun DrawerItem(title: String, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(title, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
        icon = { Icon(icon, contentDescription = title) },
        selected = isSelected,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
fun PlaceholderScreen(title: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text(text = "هنا سنبني الواجهة قريباً", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    }
}

fun getScreenTitle(route: String): String {
    return when(route) {
        "dashboard" -> "لوحة التحكم"
        "sales" -> "المبيعات"
        "purchases" -> "المشتريات"
        "expenses" -> "المصاريف"
        "inventory" -> "المخزون"
        "sales_log" -> "سجل المبيعات"
        "suppliers" -> "الموردون"
        "reports" -> "التقارير"
        "settings" -> "الإعدادات"
        else -> "صيدليتي"
    }
}