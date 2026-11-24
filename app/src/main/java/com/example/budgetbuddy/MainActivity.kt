
package com.example.budgetbuddy

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ===================== THEME =====================
private val Coral = Color(0xFFF09A73)
private val DarkBg = Color(0xFF14241D)
private val White = Color(0xFFF4F7F3)
private data class Palette(val bg: Color, val card: Color, val accent: Color,
                           val text: Color, val muted: Color, val border: Color, val button: Color)
private val dark = Palette(DarkBg, Color(0xFF1F342A), Color(0xFF2B5848), White,
    Color(0xFF9CB4A7), Color(0xFF355145), Color(0xFF285142))
private val light = Palette(Color(0xFFF3F6F1), Color.White, Color(0xFFD4E9DD),
    Color(0xFF173429), Color(0xFF607C6E), Color(0xFFD3E0D7), Color(0xFF26634C))
private val LocalPalette = staticCompositionLocalOf { dark }

// ===================== DATA =====================
private data class Expense(val id: Long, val title: String, val type: String,
                           val category: String, val amount: Double, val date: Long, val receipt: String = "")
private data class BudgetState(
    val allowance: Double = 0.0,
    val expenses: List<Expense> = emptyList(),
    val goalName: String = "", val goalTarget: Double = 0.0, val goalSaved: Double = 0.0,
    val userName: String = "", val profilePhoto: String = "",
    val darkMode: Boolean = true, val fontSize: Int = 1
)

// Keep preference names unchanged to preserve data from earlier BudgetBuddy versions.
private object BudgetStorage {
    private const val PREFS = "budgetbuddy_preferences"
    fun load(context: Context): BudgetState {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val entries = mutableListOf<Expense>()
        try {
            val array = JSONArray(p.getString("expenses", "[]") ?: "[]")
            for (i in 0 until array.length()) {
                val e = array.getJSONObject(i)
                entries.add(Expense(e.getLong("id"), e.getString("title"),
                    e.getString("type"), e.getString("category"), e.getDouble("amount"),
                    e.getLong("date"), e.optString("receipt", "")))
            }
        } catch (_: Exception) { }
        fun number(key: String) = p.getString(key, "0")?.toDoubleOrNull() ?: 0.0
        return BudgetState(
            allowance = number("allowance"), expenses = entries,
            goalName = p.getString("goalName", "") ?: "",
            goalTarget = number("goalTarget"), goalSaved = number("goalSaved"),
            userName = p.getString("userName", "") ?: "",
            profilePhoto = p.getString("profilePhoto", "") ?: "",
            darkMode = p.getBoolean("darkMode", true), fontSize = p.getInt("fontSize", 1)
        )
    }
    fun save(context: Context, s: BudgetState) {
        val array = JSONArray()
        s.expenses.forEach { e ->
            array.put(JSONObject().apply {
                put("id", e.id); put("title", e.title); put("type", e.type)
                put("category", e.category); put("amount", e.amount)
                put("date", e.date); put("receipt", e.receipt)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("expenses", array.toString())
            .putString("allowance", s.allowance.toString())
            .putString("goalName", s.goalName)
            .putString("goalTarget", s.goalTarget.toString())
            .putString("goalSaved", s.goalSaved.toString())
            .putString("userName", s.userName)
            .putString("profilePhoto", s.profilePhoto)
            .putBoolean("darkMode", s.darkMode)
            .putInt("fontSize", s.fontSize).apply()
    }
}

private fun peso(n: Double) = String.format(Locale.US, "₱%,.2f", n)
private fun dateText(n: Long) = SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH).format(Date(n))
private fun moneyNumber(s: String) = s.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
private fun monthExpenses(items: List<Expense>): List<Expense> {
    val now = Calendar.getInstance()
    return items.filter {
        val date = Calendar.getInstance().apply { timeInMillis = it.date }
        date.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                date.get(Calendar.MONTH) == now.get(Calendar.MONTH)
    }
}
private fun spent(items: List<Expense>, type: String? = null) =
    items.filter { type == null || it.type == type }.sumOf { it.amount }
private fun toast(context: Context, text: String) =
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

// ===================== PHOTOS =====================
private fun copyImage(context: Context, from: Uri, folder: String): String? = try {
    val dir = File(context.filesDir, folder).apply { mkdirs() }
    val file = File.createTempFile("photo_", ".jpg", dir)
    val input = context.contentResolver.openInputStream(from) ?: error("No image")
    input.use { stream ->
        file.outputStream().use { output ->
            stream.copyTo(output)
        }
    }
    Uri.fromFile(file).toString()
} catch (_: Exception) { null }

private fun saveBitmap(context: Context, image: Bitmap): String? = try {
    val dir = File(context.filesDir, "receipts").apply { mkdirs() }
    val file = File.createTempFile("receipt_", ".jpg", dir)
    file.outputStream().use { image.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    Uri.fromFile(file).toString()
} catch (_: Exception) { null }
private fun photo(context: Context, address: String): Bitmap? {
    if (address.isBlank()) return null
    return try {
        val uri = Uri.parse(address)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null,
                BitmapFactory.Options().apply { inSampleSize = sample })
        }
    } catch (_: Exception) { null }
}
private fun cameraUri(context: Context): Uri? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    return try {
        val data = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "budgetbuddy_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/BudgetBuddy")
        }
        context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, data)
    } catch (_: Exception) { null }
}
private fun removeTemp(context: Context, uri: Uri) {
    try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) { }
}

// ===================== NOTIFICATIONS =====================
private const val CHANNEL_ID = "budgetbuddy_budget_updates"
private fun createBudgetChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(CHANNEL_ID, "BudgetBuddy alerts",
            NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Expense confirmations and budget limit warnings"
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}
private fun canNotify(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

private fun postBudgetNotification(
    context: Context, title: String, message: String, openTab: Int, id: Int
) {
    if (!canNotify(context)) return
    createBudgetChannel(context)
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra("open_tab", openTab)
    }
    val pending = PendingIntent.getActivity(context, id, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        Notification.Builder(context, CHANNEL_ID) else Notification.Builder(context)
    val notification = builder
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(message)
        .setStyle(Notification.BigTextStyle().bigText(message))
        .setAutoCancel(true)
        .setContentIntent(pending)
        .setVisibility(Notification.VISIBILITY_PRIVATE)
        .build()
    context.getSystemService(Context.NOTIFICATION_SERVICE)
        .let { it as NotificationManager }.notify(id, notification)
}

// Check crossing: not repeatedly notify after 80%/100% was already reached.
private fun budgetWarning(before: BudgetState, entry: Expense): Pair<String, String>? {
    val categoryLimit = before.allowance * if (entry.type == "Need") 0.50 else 0.30
    if (categoryLimit <= 0 || entry !in monthExpenses(listOf(entry))) return null
    val previous = spent(monthExpenses(before.expenses), entry.type)
    val current = previous + entry.amount
    val categoryName = if (entry.type == "Need") "Needs" else "Wants"
    return when {
        previous < categoryLimit && current >= categoryLimit ->
            "${categoryName} budget reached!" to
                    "You've spent ${peso(current)} of your ${peso(categoryLimit)} ${categoryName} limit (100%+)."
        previous < categoryLimit * .80 && current >= categoryLimit * .80 ->
            "${categoryName} budget at 80%!" to
                    "You've spent ${peso(current)} of your ${peso(categoryLimit)} ${categoryName} limit."
        else -> null
    }
}

// ===================== ACTIVITY AND APP =====================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createBudgetChannel(this)
        setContent { BudgetBuddyApp() }
    }
}

@Composable
private fun BudgetBuddyApp() {
    val context = LocalContext.current
    var state by remember { mutableStateOf(BudgetStorage.load(context)) }
    val activity = context as? Activity
    var tab by rememberSaveable { mutableIntStateOf(activity?.intent?.getIntExtra("open_tab", 0) ?: 0) }
    var addExpense by rememberSaveable { mutableStateOf(false) }
    var editAllowance by remember { mutableStateOf(false) }
    var editGoal by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(false) }
    var clearHistory by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) toast(context, "Notifications disabled. You can enable them in Settings.")
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !canNotify(context))
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    fun update(next: BudgetState) {
        state = next
        BudgetStorage.save(context, next)
    }
    val colors = if (state.darkMode) dark else light
    val density = LocalDensity.current
    val multiplier = when (state.fontSize) { 0 -> .9f; 2 -> 1.12f; else -> 1f }

    CompositionLocalProvider(LocalPalette provides colors,
        LocalDensity provides Density(density.density, density.fontScale * multiplier)) {
        MaterialTheme(colorScheme = if (state.darkMode) darkColorScheme(
            primary = Coral, background = colors.bg, surface = colors.card,
            onSurface = colors.text, onBackground = colors.text
        ) else lightColorScheme(primary = colors.button, background = colors.bg,
            surface = colors.card, onSurface = colors.text, onBackground = colors.text)) {
            Scaffold(containerColor = colors.bg,
                bottomBar = { BottomNav(tab, { tab = it }, { addExpense = true }) }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when (tab) {
                        0 -> HomeScreen(state, { addExpense = true }, { tab = 2 },
                            { tab = 1 }, { editGoal = true })
                        1 -> ExpensesScreen(state) { id ->
                            update(state.copy(expenses = state.expenses.filterNot { it.id == id }))
                        }
                        2 -> MonthlyBudgetScreen(state, { editAllowance = true }, { editGoal = true })
                        3 -> ProfileScreen(state, { editAllowance = true }, { tab = 1 },
                            { editName = true }, { uri ->
                                update(state.copy(profilePhoto = uri))
                                toast(context, "Profile picture updated!")
                            }, { darkMode -> update(state.copy(darkMode = darkMode)) },
                            { font -> update(state.copy(fontSize = font)) }, { clearHistory = true })
                    }
                }
            }
            if (addExpense) ExpenseForm({ addExpense = false }) { entry ->
                val warning = budgetWarning(state, entry)
                update(state.copy(expenses = state.expenses + entry))
                addExpense = false
                toast(context, "Expense added successfully!")
                postBudgetNotification(context, "Expense successfully recorded",
                    "${entry.title} • ${entry.category} • ${peso(entry.amount)}",
                    1, (System.currentTimeMillis() % 1000000).toInt())
                if (warning != null) postBudgetNotification(context,
                    warning.first, warning.second, 2,
                    ((System.currentTimeMillis() + 1) % 1000000).toInt())
            }
            if (editAllowance) AllowanceDialog(state.allowance, { editAllowance = false }) {
                update(state.copy(allowance = it))
                editAllowance = false
                toast(context, "Monthly allowance updated!")
            }
            if (editGoal) GoalDialog(state, { editGoal = false }) { name, target, saved ->
                update(state.copy(goalName = name, goalTarget = target, goalSaved = saved))
                editGoal = false
            }
            if (editName) NameDialog(state.userName, { editName = false }) {
                update(state.copy(userName = it)); editName = false
            }
            if (clearHistory) AlertDialog(onDismissRequest = { clearHistory = false },
                title = { Text("Clear expense history?") },
                text = { Text("This will permanently remove your recorded expenses.") },
                confirmButton = { TextButton(onClick = {
                    update(state.copy(expenses = emptyList())); clearHistory = false
                }) { Text("Clear") } },
                dismissButton = { TextButton(onClick = { clearHistory = false }) { Text("Cancel") } })
        }
    }
}

// ===================== COMMON UI =====================
@Composable private fun CardBlock(
    modifier: Modifier = Modifier, accent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = LocalPalette.current
    Surface(modifier, shape = RoundedCornerShape(15.dp),
        color = if (accent) c.accent else c.card, contentColor = c.text) {
        Column(Modifier.padding(17.dp), content = content)
    }
}
@Composable private fun Heading(title: String, subtitle: String = "",
                                action: String = "", onAction: () -> Unit = {}) {
    val c = LocalPalette.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotBlank()) Text(subtitle, color = c.muted, fontSize = 12.sp)
        }
        if (action.isNotBlank()) Text(action, color = c.muted, fontSize = 12.sp,
            modifier = Modifier.clickable { onAction() }.padding(5.dp))
    }
}
@Composable private fun Bar(used: Double, limit: Double, tint: Color = Coral) {
    val c = LocalPalette.current
    LinearProgressIndicator(progress = { if (limit > 0) (used / limit).toFloat().coerceIn(0f, 1f) else 0f },
        modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
        color = tint, trackColor = c.border)
}
@Composable private fun Ring(progress: Float, size: Int = 54) {
    val c = LocalPalette.current
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawArc(c.border, -90f, 360f, false,
                style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
            if (progress > 0) drawArc(Coral, -90f, progress.coerceIn(0f, 1f) * 360f, false,
                style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
        }
        Text("${(progress.coerceIn(0f,1f) * 100).toInt()}%",
            fontWeight = FontWeight.Bold, fontSize = 10.sp, color = c.text)
    }
}
@Composable private fun BottomNav(selected: Int, onSelect: (Int) -> Unit, onAdd: () -> Unit) {
    val c = LocalPalette.current
    Row(Modifier.fillMaxWidth().background(c.bg).navigationBarsPadding().height(64.dp)
        .padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(0 to "Homepage", 1 to "Expenses").forEach { (index, label) ->
            NavItem(index, label, if (index == 0) "⌂" else "▤", selected,
                Modifier.weight(1f), onSelect)
        }
        Box(Modifier.weight(.95f), contentAlignment = Alignment.Center) {
            Box(Modifier.size(47.dp).background(Coral, CircleShape).clickable { onAdd() },
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, "Add expense", tint = DarkBg,
                    modifier = Modifier.size(27.dp))
            }
        }
        NavItem(2, "Budget", "♧", selected, Modifier.weight(1f), onSelect)
        NavItem(3, "Profile", "♙", selected, Modifier.weight(1f), onSelect)
    }
}
@Composable private fun NavItem(index: Int, label: String, symbol: String,
                                selected: Int, modifier: Modifier, onSelect: (Int) -> Unit) {
    val c = LocalPalette.current
    Column(modifier.clip(RoundedCornerShape(12.dp)).clickable { onSelect(index) }
        .background(if (selected == index) c.card else Color.Transparent)
        .padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(symbol, fontSize = 23.sp, color = if (selected == index) c.text else c.muted)
        Text(label, fontSize = 10.sp, color = if (selected == index) c.text else c.muted,
            maxLines = 1)
    }
}
@Composable private fun EmptyState() {
    val c = LocalPalette.current
    Column(Modifier.fillMaxWidth().height(120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text("▱", fontSize = 29.sp, color = c.muted)
        Text("No expenses yet", fontSize = 14.sp, color = c.muted)
        Text("Add your first daily expense to see it here.", fontSize = 11.sp, color = c.muted)
    }
}
@Composable private fun ExpenseRow(e: Expense, onClick: () -> Unit = {}, onDelete: (() -> Unit)? = null) {
    val c = LocalPalette.current
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(c.accent, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center) {
            Text(if (e.type == "Need") "⌂" else "✦", fontSize = 20.sp,
                color = if (e.type == "Need") c.text else Coral)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, fontWeight = FontWeight.SemiBold, color = c.text, fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${e.category} • ${dateText(e.date)}", fontSize = 10.sp, color = c.muted)
            if (e.receipt.isNotBlank()) Text("▣ Receipt attached", fontSize = 10.sp, color = c.muted)
        }
        Text("-${peso(e.amount)}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = c.text)
        if (onDelete != null) IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, "Delete", tint = c.muted, modifier = Modifier.size(18.dp))
        }
    }
}
@Composable private fun CategoryCard(title: String, amount: Double, limit: Double,
                                     symbol: String, modifier: Modifier = Modifier) {
    val c = LocalPalette.current
    CardBlock(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(symbol, color = if (title == "Wants") Coral else c.text, fontSize = 22.sp)
            Ring(if (limit > 0) (amount / limit).toFloat() else 0f, 43)
        }
        Spacer(Modifier.height(10.dp))
        Text(title, color = c.muted, fontSize = 12.sp)
        Text(peso(amount), color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text("of ${peso(limit)} budget", fontSize = 10.sp, color = c.muted)
    }
}
@Composable private fun QuickAction(symbol: String, title: String,
                                    subtitle: String, onClick: () -> Unit) {
    val c = LocalPalette.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
        .background(c.border.copy(alpha = .4f)).clickable { onClick() }.padding(11.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(symbol, color = Coral, fontSize = 20.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = c.muted, fontSize = 10.sp)
        }
        Icon(Icons.Default.Add, null, tint = c.muted, modifier = Modifier.size(17.dp))
    }
}

// ===================== HOME =====================
@Composable private fun HomeScreen(s: BudgetState, onAdd: () -> Unit, onBudget: () -> Unit,
                                   onExpenses: () -> Unit, onGoal: () -> Unit) {
    val c = LocalPalette.current
    val m = monthExpenses(s.expenses)
    val all = spent(m); val needs = spent(m, "Need"); val wants = spent(m, "Want")
    val spendable = s.allowance * .8; val available = spendable - all
    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 0..11 -> "Good morning"; in 12..17 -> "Good afternoon"; else -> "Good evening"
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(16.dp).padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("B u d g e t B u d d y", color = Coral, fontSize = 10.sp,
            fontWeight = FontWeight.Bold)
        Text(greeting, color = c.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Here's how your money is looking this month.", color = c.muted, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        CardBlock(Modifier.fillMaxWidth(), accent = true) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Available to spend", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(if (available >= 0) "● On track" else "● Over budget", fontSize = 10.sp)
            }
            Spacer(Modifier.height(17.dp))
            Text(peso(available), fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("of ${peso(spendable)} spendable allowance", fontSize = 12.sp)
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${peso(all)} spent", fontSize = 10.sp)
                Text("${if (spendable > 0) (all / spendable * 100).toInt() else 0}% used",
                    fontSize = 10.sp)
            }
            Spacer(Modifier.height(7.dp))
            Bar(all, spendable, c.text)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            CategoryCard("Needs", needs, s.allowance * .5, "⌂", Modifier.weight(1f))
            CategoryCard("Wants", wants, s.allowance * .3, "✦", Modifier.weight(1f))
        }
        CardBlock(Modifier.fillMaxWidth()) {
            Heading("Monthly spending", "Needs and wants at a glance", "Edit budget ›", onBudget)
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = 18.dp.toPx()
                        drawArc(c.border, -90f, 360f, false, style = Stroke(stroke))
                        if (all > 0) {
                            val sweep = if (all > 0) (needs / all * 360).toFloat() else 0f
                            drawArc(c.text, -90f, sweep, false, style = Stroke(stroke))
                            drawArc(Coral, -90f + sweep,
                                (wants / all * 360).toFloat(), false, style = Stroke(stroke))
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(peso(all), fontWeight = FontWeight.Bold,
                            fontSize = 18.sp, color = c.text)
                        Text("Total spent", fontSize = 10.sp, color = c.muted)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = c.border)
            listOf(Triple("Needs", needs, s.allowance * .5),
                Triple("Wants", wants, s.allowance * .3)).forEachIndexed { index, item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(if (index == 0) c.text else Coral,
                        CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(item.first, Modifier.weight(1f), color = c.muted, fontSize = 11.sp)
                    Text(peso(item.second), color = c.text, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(14.dp))
                    Text("${if (item.third > 0) (item.second / item.third * 100).toInt() else 0}%",
                        color = c.muted, fontSize = 11.sp)
                }
            }
            HorizontalDivider(color = c.border)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("▣", fontSize = 23.sp, color = c.text)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Protected savings", fontSize = 11.sp, color = c.muted)
                    Text(peso(s.allowance * .2), fontWeight = FontWeight.Bold, color = c.text)
                }
                Text("20% of allowance", fontSize = 10.sp, color = c.text)
            }
        }
        CardBlock(Modifier.fillMaxWidth()) {
            Heading("Quick add", "Log a daily expense")
            Spacer(Modifier.height(12.dp))
            QuickAction("⌂", "Add a need", "Groceries, bills, rent", onAdd)
            Spacer(Modifier.height(8.dp))
            QuickAction("✦", "Add a want", "Dining, fun, shopping", onAdd)
            Spacer(Modifier.height(10.dp))
            Text("ⓘ Try keeping needs below 50% and wants below 30% of your allowance.",
                fontSize = 11.sp, color = c.muted)
        }
        CardBlock(Modifier.fillMaxWidth()) {
            Heading("Dream purchases", "Save separately for something important", "+ New goal", onGoal)
            Spacer(Modifier.height(16.dp))
            if (s.goalName.isBlank()) Column(Modifier.fillMaxWidth().clickable { onGoal() }
                .padding(vertical = 23.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("◎", fontSize = 32.sp, color = c.muted)
                Text("What are you saving for?", fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp, color = c.text)
                Text("Create a goal and track every peso you save.",
                    color = c.muted, fontSize = 10.sp)
            } else {
                Text(s.goalName, fontWeight = FontWeight.Bold, color = c.text)
                Text("${peso(s.goalSaved)} of ${peso(s.goalTarget)}", color = c.muted)
                Spacer(Modifier.height(10.dp))
                Bar(s.goalSaved, s.goalTarget)
                TextButton(onClick = onGoal) { Text("Edit goal") }
            }
        }
        CardBlock(Modifier.fillMaxWidth()) {
            Heading("Recent expenses", "Your latest daily spending", "All expenses ›", onExpenses)
            Spacer(Modifier.height(12.dp))
            val recent = s.expenses.sortedByDescending { it.date }.take(4)
            if (recent.isEmpty()) EmptyState() else recent.forEach { ExpenseRow(it)
                HorizontalDivider(color = c.border) }
        }
    }
}

// ===================== EXPENSES =====================
@Composable private fun ExpensesScreen(s: BudgetState, onDelete: (Long) -> Unit) {
    val c = LocalPalette.current; val context = LocalContext.current
    var filter by rememberSaveable { mutableStateOf("All expenses") }
    var delete by remember { mutableStateOf<Expense?>(null) }
    var selected by remember { mutableStateOf<Expense?>(null) }
    val month = monthExpenses(s.expenses)
    val shown = month.filter { filter == "All expenses" ||
            it.type == if (filter == "Needs") "Need" else "Want" }.sortedByDescending { it.date }
    val spendable = s.allowance * .8; val used = spent(month)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("D A I L Y   S P E N D I N G", color = c.muted, fontSize = 10.sp)
            Spacer(Modifier.height(7.dp))
            Text("Expenses", color = c.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Track every need and want in one place.", color = c.muted, fontSize = 13.sp)
        }
        item {
            CardBlock(Modifier.fillMaxWidth(), accent = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Spent this month", fontSize = 11.sp)
                        Text(peso(used), fontWeight = FontWeight.Bold, fontSize = 26.sp)
                        Text("${peso(spendable - used)} still available", fontSize = 11.sp)
                    }
                    Ring(if (spendable > 0) (used / spendable).toFloat() else 0f, 65)
                }
            }
        }
        item { Heading("All transactions", "${shown.size} recorded expenses") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("All expenses", "Needs", "Wants").forEach { choice ->
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(7.dp))
                        .background(if (filter == choice) c.border else Color.Transparent)
                        .clickable { filter = choice }.padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center) {
                        Text(choice, color = if (filter == choice) c.text else c.muted,
                            fontSize = 11.sp)
                    }
                }
            }
        }
        if (shown.isEmpty()) item { CardBlock(Modifier.fillMaxWidth()) { EmptyState() } }
        else items(shown, key = { it.id }) { e ->
            CardBlock(Modifier.fillMaxWidth()) {
                ExpenseRow(e, onClick = { selected = e }, onDelete = { delete = e })
            }
        }
    }
    delete?.let { e ->
        AlertDialog(onDismissRequest = { delete = null }, title = { Text("Delete expense?") },
            text = { Text("Remove ${e.title} from your transactions?") },
            confirmButton = { TextButton(onClick = { onDelete(e.id); delete = null }) {
                Text("Delete") } },
            dismissButton = { TextButton(onClick = { delete = null }) { Text("Cancel") } })
    }
    selected?.let { e ->
        val receiptBitmap = remember(e.receipt) { photo(context, e.receipt) }
        AlertDialog(onDismissRequest = { selected = null },
            title = { Text(e.title) }, text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("${e.type} • ${e.category}")
                    Text(dateText(e.date))
                    Text(peso(e.amount))
                    if (receiptBitmap != null) Image(receiptBitmap.asImageBitmap(), "Receipt photo",
                        Modifier.fillMaxWidth().height(230.dp), contentScale = ContentScale.Fit)
                    else Text(if (e.receipt.isBlank()) "No receipt attached" else "Receipt unavailable")
                }
            }, confirmButton = { TextButton(onClick = { selected = null }) { Text("Close") } })
    }
}

// ===================== MONTHLY BUDGET =====================
@Composable private fun MonthlyBudgetScreen(s: BudgetState, edit: () -> Unit, goal: () -> Unit) {
    val c = LocalPalette.current
    val month = monthExpenses(s.expenses)
    val needs = spent(month, "Need"); val wants = spent(month, "Want")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("5 0  /  3 0  /  2 0", color = Coral, fontSize = 10.sp)
                Text("Monthly budget", color = c.text, fontSize = 27.sp,
                    fontWeight = FontWeight.Bold)
                Text("Your allowance is automatically divided for you.",
                    color = c.muted, fontSize = 13.sp)
            }
            IconButton(onClick = edit, modifier = Modifier.background(c.card,
                RoundedCornerShape(11.dp))) {
                Icon(Icons.Default.Settings, "Edit allowance", tint = c.text)
            }
        }
        Spacer(Modifier.height(8.dp))
        CardBlock(Modifier.fillMaxWidth(), accent = true) {
            Text("Monthly allowance", fontSize = 12.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(peso(s.allowance), Modifier.weight(1f), fontSize = 27.sp,
                    fontWeight = FontWeight.Bold)
                TextButton(onClick = edit) { Text("Edit allowance ›", fontSize = 11.sp) }
            }
        }
        BudgetAllocation("50% - Needs", "${peso(needs)} used", "⌂", s.allowance * .5, needs, c.text)
        BudgetAllocation("30% - Wants", "${peso(wants)} used", "✦", s.allowance * .3, wants, Coral)
        BudgetAllocation("20% - Protected savings", "Reserved from spending", "▣",
            s.allowance * .2, 0.0, c.text)
        CardBlock(Modifier.fillMaxWidth().clickable { goal() }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("◎", color = Coral, fontSize = 28.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Dream purchases", fontWeight = FontWeight.Bold,
                        color = c.text, fontSize = 12.sp)
                    Text(if (s.goalName.isBlank()) "Create a separate savings goal"
                    else "${s.goalName}: ${peso(s.goalSaved)} saved",
                        color = c.muted, fontSize = 10.sp)
                }
                Text("›", color = c.muted, fontSize = 24.sp)
            }
        }
    }
}
@Composable private fun BudgetAllocation(title: String, subtitle: String, symbol: String,
                                         amount: Double, used: Double, tint: Color) {
    val c = LocalPalette.current
    CardBlock(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(39.dp).background(c.accent, RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center) {
                Text(symbol, fontSize = 21.sp, color = tint)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = c.text, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = c.muted, fontSize = 10.sp)
            }
            Text(peso(amount), fontWeight = FontWeight.Bold,
                fontSize = 13.sp, color = c.text)
        }
        Spacer(Modifier.height(14.dp))
        Bar(used, amount, tint)
    }
}

// ===================== PROFILE =====================
@Composable private fun ProfileScreen(s: BudgetState, onBudget: () -> Unit,
                                      onHistory: () -> Unit, onName: () -> Unit, onPhoto: (String) -> Unit,
                                      onTheme: (Boolean) -> Unit, onFont: (Int) -> Unit, onClear: () -> Unit) {
    val c = LocalPalette.current; val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val saved = copyImage(context, uri, "profile_photos")
            if (saved != null) onPhoto(saved)
            else toast(context, "Unable to save profile picture")
        }
    }
    val face = remember(s.profilePhoto) { photo(context, s.profilePhoto) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(78.dp).clip(CircleShape).background(c.accent)
                .clickable { picker.launch("image/*") }, contentAlignment = Alignment.Center) {
                if (face != null) Image(face.asImageBitmap(), "Profile picture",
                    Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Default.Person, "Choose profile picture", tint = c.text,
                    modifier = Modifier.size(35.dp))
            }
            TextButton(onClick = { picker.launch("image/*") }) {
                Text("Edit profile picture", color = c.muted, fontSize = 11.sp)
            }
            Text(if (s.userName.isBlank()) "Your account" else s.userName,
                color = c.text, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onName, colors = ButtonDefaults.buttonColors(
                containerColor = c.button, contentColor = White)) {
                Text(if (s.userName.isBlank()) "Add your account" else "Edit your account",
                    fontSize = 12.sp)
            }
        }
        CardBlock(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(peso(s.allowance), color = c.text, fontWeight = FontWeight.Bold)
                    Text("Monthly allowance", color = c.muted, fontSize = 10.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${s.expenses.size}", color = c.text, fontWeight = FontWeight.Bold)
                    Text("Purchase entries", color = c.muted, fontSize = 10.sp)
                }
            }
        }
        CardBlock(Modifier.fillMaxWidth()) {
            ProfileLink("▥", "Budget preferences", "Update your monthly allowance", onBudget)
            HorizontalDivider(color = c.border)
            ProfileLink("▤", "Expense history", "Review needs, wants and receipts", onHistory)
            HorizontalDivider(color = c.border)
            ProfileLink("♙", "Personal details", "Add your account information", onName)
        }
        CardBlock(Modifier.fillMaxWidth()) {
            Heading("Appearance", "Make BudgetBuddy truly yours")
            Spacer(Modifier.height(17.dp))
            Text("App theme", color = c.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeChoice("☀", "Light", !s.darkMode, Modifier.weight(1f)) { onTheme(false) }
                ThemeChoice("☾", "Dark", s.darkMode, Modifier.weight(1f)) { onTheme(true) }
            }
            Spacer(Modifier.height(19.dp))
            Text("Font size", color = c.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Small", "Medium", "Large").forEachIndexed { index, label ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                        .background(if (s.fontSize == index) c.accent else c.bg)
                        .clickable { onFont(index) }.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Aa", fontSize = (14 + index * 2).sp, color = c.text,
                            fontWeight = FontWeight.Bold)
                        Text(label, fontSize = 10.sp, color = c.muted)
                    }
                }
            }
            TextButton(onClick = { onFont(1); onTheme(true) }, Modifier.fillMaxWidth()) {
                Text("Reset appearance to default", fontSize = 11.sp)
            }
        }
        OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
            Text("Clear expense history", color = c.text)
        }
        Text("✓ Your data is saved privately on this device.", Modifier.fillMaxWidth(),
            color = c.muted, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}
@Composable private fun ProfileLink(symbol: String, title: String, detail: String,
                                    onClick: () -> Unit) {
    val c = LocalPalette.current
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(symbol, fontSize = 24.sp, color = c.text)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = c.text)
            Text(detail, fontSize = 10.sp, color = c.muted)
        }
        Text("›", fontSize = 24.sp, color = c.muted)
    }
}
@Composable private fun ThemeChoice(symbol: String, label: String, selected: Boolean,
                                    modifier: Modifier, onClick: () -> Unit) {
    val c = LocalPalette.current
    Row(modifier.clip(RoundedCornerShape(10.dp))
        .background(if (selected) c.accent else c.bg)
        .clickable { onClick() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(symbol, color = c.text, fontSize = 19.sp)
        Spacer(Modifier.width(8.dp))
        Text(label, color = c.text, fontSize = 12.sp, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Default.Check, null, tint = c.text,
            modifier = Modifier.size(14.dp))
    }
}

// ===================== EXPENSE FORM WITH CAMERA INTENT =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ExpenseForm(onDismiss: () -> Unit, onSave: (Expense) -> Unit) {
    val c = LocalPalette.current; val context = LocalContext.current
    var type by rememberSaveable { mutableStateOf("Need") }
    var description by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Housing") }
    var date by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var receipt by rememberSaveable { mutableStateOf("") }
    var cameraPending by rememberSaveable { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val categories = listOf("Housing", "Groceries", "Food", "Transportation", "School",
        "Utilities", "Healthcare", "Shopping", "Entertainment", "Other")

    // An implicit intent to pick an existing image.
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) receipt = copyImage(context, uri, "receipts") ?: run {
            toast(context, "Unable to upload receipt"); ""
        }
    }
    // An implicit camera intent with a full-resolution destination on Android 10+.
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (cameraPending.isNotBlank()) {
            val uri = Uri.parse(cameraPending)
            if (ok) receipt = copyImage(context, uri, "receipts") ?: run {
                toast(context, "Unable to save receipt photo"); ""
            }
            removeTemp(context, uri)
        }
        cameraPending = ""
    }
    // Older devices: camera thumbnail fallback; does not need FileProvider.
    val oldCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) receipt = saveBitmap(context, bitmap) ?: run {
            toast(context, "Unable to save receipt photo"); ""
        }
    }
    val number = moneyNumber(amount)
    val canSave = description.isNotBlank() && number != null

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.card,
        contentColor = c.text, sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("D A I L Y   S P E N D I N G", fontSize = 10.sp, color = c.muted)
                    Text("Add an expense", fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    Text("Record it now, stay on track later.", fontSize = 12.sp, color = c.muted)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Need", "Want").forEach { choice ->
                    OutlinedButton(onClick = { type = choice }, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (type == choice) c.accent else Color.Transparent,
                            contentColor = c.text)) {
                        Text(if (choice == "Need") "⌂ Need" else "✦ Want", fontSize = 12.sp)
                    }
                }
            }
            Text("What did you spend on?", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            OutlinedTextField(description, { description = it }, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. Weekly groceries") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Amount", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(amount, { amount = it },
                        placeholder = { Text("₱ 0.00") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                Column(Modifier.weight(1f)) {
                    Text("Date", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = {
                        val current = Calendar.getInstance().apply { timeInMillis = date }
                        DatePickerDialog(context, { _, year, month, day ->
                            date = Calendar.getInstance().apply {
                                set(year, month, day, 12, 0, 0)
                                set(Calendar.MILLISECOND, 0)
                            }.timeInMillis
                        }, current.get(Calendar.YEAR), current.get(Calendar.MONTH),
                            current.get(Calendar.DAY_OF_MONTH)).show()
                    }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text(SimpleDateFormat("MM/dd/yyyy", Locale.US).format(Date(date)),
                            fontSize = 12.sp)
                    }
                }
            }
            Text("Category", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(category, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                    Text("⌄")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    categories.forEach { choice ->
                        DropdownMenuItem(text = { Text(choice) },
                            onClick = { category = choice; expanded = false })
                    }
                }
            }
            Text("Proof of purchase", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Optional", color = c.muted, fontSize = 10.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val uri = cameraUri(context)
                        if (uri == null) toast(context, "Unable to prepare camera")
                        else {
                            cameraPending = uri.toString()
                            try { camera.launch(uri) } catch (_: Exception) {
                                removeTemp(context, uri)
                                cameraPending = ""
                                toast(context, "Could not open camera")
                            }
                        }
                    } else oldCamera.launch(null)
                }, modifier = Modifier.weight(1f)) { Text("📷 Scan receipt", fontSize = 11.sp,
                    maxLines = 1) }
                OutlinedButton(onClick = { gallery.launch("image/*") },
                    modifier = Modifier.weight(1f)) {
                    Text("▣ Upload receipt", fontSize = 11.sp, maxLines = 1)
                }
            }
            if (receipt.isNotBlank()) {
                val picture = remember(receipt) { photo(context, receipt) }
                CardBlock(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✓ Receipt attached", Modifier.weight(1f), fontSize = 12.sp)
                        TextButton(onClick = { receipt = "" }) { Text("Remove") }
                    }
                    if (picture != null) Image(picture.asImageBitmap(), "Receipt preview",
                        Modifier.fillMaxWidth().height(180.dp), contentScale = ContentScale.Fit)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = {
                    if (canSave) onSave(Expense(System.nanoTime(), description.trim(),
                        type, category, number!!, date, receipt))
                }, enabled = canSave, modifier = Modifier.weight(1f).height(46.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = c.button, contentColor = White)) {
                    Text("Add expense")
                }
            }
        }
    }
}

// ===================== EDIT DIALOGS =====================
@Composable private fun AllowanceDialog(current: Double, onDismiss: () -> Unit,
                                        onSave: (Double) -> Unit) {
    var amount by remember { mutableStateOf(if (current == 0.0) "" else current.toString()) }
    val number = amount.toDoubleOrNull()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Monthly allowance") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Enter the money you receive each month.", fontSize = 13.sp)
            OutlinedTextField(amount, { amount = it }, label = { Text("Amount (₱)") },
                singleLine = true, keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal))
            Text("50% Needs • 30% Wants • 20% Savings", fontSize = 11.sp)
        }
    }, confirmButton = {
        TextButton(onClick = { number?.let(onSave) },
            enabled = number != null && number.isFinite() && number >= 0.0) { Text("Save") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
@Composable private fun GoalDialog(s: BudgetState, onDismiss: () -> Unit,
                                   onSave: (String, Double, Double) -> Unit) {
    var name by remember { mutableStateOf(s.goalName) }
    var target by remember { mutableStateOf(if (s.goalTarget > 0) s.goalTarget.toString() else "") }
    var saved by remember { mutableStateOf(if (s.goalSaved > 0) s.goalSaved.toString() else "") }
    val goal = moneyNumber(target)
    val already = saved.toDoubleOrNull() ?: 0.0
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Dream purchase") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("What are you saving for?") },
                singleLine = true)
            OutlinedTextField(target, { target = it }, label = { Text("Target amount (₱)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true)
            OutlinedTextField(saved, { saved = it }, label = { Text("Already saved (₱)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true)
        }
    }, confirmButton = {
        TextButton(onClick = { if (goal != null) onSave(name.trim(), goal, already) },
            enabled = name.isNotBlank() && goal != null && already.isFinite() && already >= 0) {
            Text("Save goal")
        }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
@Composable private fun NameDialog(current: String, onDismiss: () -> Unit,
                                   onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Personal details") },
        text = { OutlinedTextField(name, { name = it }, label = { Text("Your name") },
            singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

// Android Studio preview. Use Run on a phone to test camera and notifications.
@Preview(name = "BudgetBuddy", showBackground = true, widthDp = 393, heightDp = 852)
@Composable fun BudgetBuddyPreview() { BudgetBuddyApp() }
