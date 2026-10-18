package iq.haji.cashier;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.print.PrintManager;
import android.print.PrintAttributes;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    private static final int NAVY = Color.rgb(19, 29, 44);
    private static final int PANEL = Color.rgb(31, 45, 63);
    private static final int TEAL = Color.rgb(0, 178, 145);
    private static final int ORANGE = Color.rgb(255, 155, 64);
    private static final int WHITE = Color.WHITE;
    private static final int MUTED = Color.rgb(185, 198, 211);
    private static final int RED = Color.rgb(190, 65, 76);

    private static final int REQUEST_EXPORT = 200;
    private static final int REQUEST_CALL_ROLE = 300;

    private LinearLayout root;
    private LinearLayout dishesLayout;
    private LinearLayout cartLayout;

    private TextView totalView;
    private TextView orderNumberView;
    private TextView statusView;

    private EditText customerNumber;
    private EditText customerAddress;

    private SharedPreferences prefs;

    private final LinkedHashMap<String, Integer> cart =
            new LinkedHashMap<>();

    private final LinkedHashMap<String, Dish> menu =
            new LinkedHashMap<>();

    private String selectedCategory = "الكل";
    private String lastCaller = "";
    private int nextOrderNumber = 1;
    private WebView printWebView;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private static class Dish {
        String name;
        String category;
        int price;

        Dish(String name, String category, int price) {
            this.name = name;
            this.category = category;
            this.price = price;
        }
    }

    private int dp(float value) {
        return (int) (value *
                getResources().getDisplayMetrics().density + 0.5f);
    }

    private String now(String pattern) {
        return new SimpleDateFormat(pattern, Locale.getDefault())
                .format(new Date());
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private TextView makeText(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(dp(5), dp(4), dp(5), dp(4));
        return view;
    }

    private Button makeButton(String label, int color) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(13);
        button.setTextColor(WHITE);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(background(color, 12));
        button.setPadding(dp(5), dp(3), dp(5), dp(3));
        return button;
    }

    private LinearLayout makeRow() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return layout;
    }

    private LinearLayout makeColumn() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return layout;
    }

    private void add(LinearLayout parent, View child,
                     int width, int height) {
        parent.addView(child, new LinearLayout.LayoutParams(
                width < 0 ? width : dp(width),
                height < 0 ? height : dp(height)));
    }

    private JSONArray readArray(String key) {
        try {
            return new JSONArray(prefs.getString(key, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        prefs = getSharedPreferences("haji_cashier", MODE_PRIVATE);
        nextOrderNumber = prefs.getInt("next_order", 1);

        loadMenu();
        buildScreen();
        refreshCart();

        handler.post(callerUpdater);
    }

    private void loadMenu() {
        menu.clear();

        JSONArray saved = readArray("menu");

        if (saved.length() > 0) {
            for (int i = 0; i < saved.length(); i++) {
                JSONObject item = saved.optJSONObject(i);
                if (item == null) continue;

                String name = item.optString("name", "");
                String category = item.optString("category", "أخرى");
                int price = item.optInt("price", 0);

                if (!name.trim().isEmpty() && price >= 0) {
                    menu.put(name, new Dish(name, category, price));
                }
            }
        }

        if (menu.isEmpty()) {
            addDefault("برغر لحم", "وجبات", 5000);
            addDefault("برغر دجاج", "وجبات", 4000);
            addDefault("زنجر", "وجبات", 5000);
            addDefault("صاج دجاج", "وجبات", 3000);
            addDefault("شاورما دجاج", "شاورما", 3000);
            addDefault("شاورما لحم", "شاورما", 4000);
            addDefault("بطاطا", "مقبلات", 1500);
            addDefault("بيبسي", "مشروبات", 500);
            addDefault("ماء", "مشروبات", 250);
            addDefault("عصير", "مشروبات", 1000);
            saveMenu();
        }
    }

    private void addDefault(String name, String category, int price) {
        menu.put(name, new Dish(name, category, price));
    }

    private void saveMenu() {
        JSONArray array = new JSONArray();

        for (Dish dish : menu.values()) {
            JSONObject object = new JSONObject();

            try {
                object.put("name", dish.name);
                object.put("category", dish.category);
                object.put("price", dish.price);
                array.put(object);
            } catch (Exception ignored) {
            }
        }

        prefs.edit().putString("menu", array.toString()).apply();
    }

    private void buildScreen() {
        root = makeColumn();
        root.setBackgroundColor(NAVY);
        root.setPadding(dp(10), dp(8), dp(10), dp(8));
        setContentView(root);

        LinearLayout header = makeRow();
        header.setBackground(background(PANEL, 16));
        header.setPadding(dp(8), dp(4), dp(8), dp(4));

        TextView title = makeText("مطعم الحجي  |  الكاشير", 20, WHITE);
        header.addView(title, new LinearLayout.LayoutParams(
                0, dp(52), 1));

        Button addDish = makeButton("إضافة طبق", ORANGE);
        addDish.setOnClickListener(v -> showDishDialog(null));
        add(header, addDish, 110, 46);

        Button callRole = makeButton("تفعيل المتصل", TEAL);
        callRole.setOnClickListener(v -> requestCallerRole());
        add(header, callRole, 130, 46);

        add(root, header, -1, 60);

        LinearLayout main = makeRow();
        LinearLayout.LayoutParams mainParams =
                new LinearLayout.LayoutParams(-1, 0, 1);
        mainParams.topMargin = dp(8);
        root.addView(main, mainParams);

        LinearLayout menuPanel = makeColumn();
        menuPanel.setBackground(background(PANEL, 16));
        menuPanel.setPadding(dp(8), dp(6), dp(8), dp(6));

        main.addView(menuPanel, new LinearLayout.LayoutParams(
                0, -1, 1.7f));

        add(menuPanel, makeText("قائمة الأطباق", 18, WHITE), -1, 38);

        ScrollView dishScroll = new ScrollView(this);
        dishesLayout = makeColumn();
        dishScroll.addView(dishesLayout);

        menuPanel.addView(dishScroll, new LinearLayout.LayoutParams(
                -1, 0, 1));

        LinearLayout cartPanel = makeColumn();
        cartPanel.setBackground(background(PANEL, 16));
        cartPanel.setPadding(dp(10), dp(6), dp(10), dp(6));

        LinearLayout.LayoutParams cartParams =
                new LinearLayout.LayoutParams(0, -1, 1f);
        cartParams.setMargins(dp(8), 0, 0, 0);
        main.addView(cartPanel, cartParams);

        add(cartPanel, makeText("الطلب الحالي", 18, WHITE), -1, 36);

        orderNumberView = makeText("", 14, TEAL);
        add(cartPanel, orderNumberView, -1, 30);

        customerNumber = makeEdit("رقم هاتف الزبون");
        customerNumber.setInputType(3);
        add(cartPanel, customerNumber, -1, 42);

        customerAddress = makeEdit("عنوان الزبون (اختياري)");
        add(cartPanel, customerAddress, -1, 42);

        LinearLayout phoneButtons = makeRow();

        Button transfer = makeButton("ترحيل الرقم", ORANGE);
        transfer.setOnClickListener(v -> {
            customerNumber.setText("");
            lastCaller = "";
            prefs.edit()
                    .remove("latest_caller")
                    .remove("caller_time")
                    .apply();
            toast("تم تجاوز رقم المتصل");
        });

        Button clear = makeButton("تفريغ السلة", RED);
        clear.setOnClickListener(v -> {
            cart.clear();
            refreshCart();
        });

        phoneButtons.addView(transfer,
                new LinearLayout.LayoutParams(0, dp(40), 1));
        phoneButtons.addView(clear,
                new LinearLayout.LayoutParams(0, dp(40), 1));

        add(cartPanel, phoneButtons, -1, 44);

        ScrollView cartScroll = new ScrollView(this);
        cartLayout = makeColumn();
        cartScroll.addView(cartLayout);

        cartPanel.addView(cartScroll, new LinearLayout.LayoutParams(
                -1, 0, 1));

        totalView = makeText("المجموع: 0 د.ع", 18, ORANGE);
        add(cartPanel, totalView, -1, 42);

        Button savePrint = makeButton("حفظ الطلب وطباعة الفاتورة", TEAL);
        savePrint.setOnClickListener(v -> saveAndPrint());
        add(cartPanel, savePrint, -1, 48);

        LinearLayout footer = makeRow();

        Button report = makeButton("تصدير حسابات اليوم", Color.rgb(64, 105, 165));
        report.setOnClickListener(v -> exportOrders());

        Button reset = makeButton("تصفير اليوم", RED);
        reset.setOnClickListener(v -> confirmReset());

        footer.addView(report, new LinearLayout.LayoutParams(0, dp(42), 1));
        footer.addView(reset, new LinearLayout.LayoutParams(0, dp(42), 1));

        LinearLayout.LayoutParams footerParams =
                new LinearLayout.LayoutParams(-1, dp(46));
        footerParams.topMargin = dp(5);
        root.addView(footer, footerParams);

        statusView = makeText("النظام يعمل محلياً", 11, MUTED);
        add(root, statusView, -1, 22);

        renderDishes();
    }

    private EditText makeEdit(String hint) {
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setTextSize(14);
        edit.setTextColor(WHITE);
        edit.setHintTextColor(MUTED);
        edit.setHint(hint);
        edit.setPadding(dp(10), 0, dp(10), 0);
        edit.setBackground(background(NAVY, 10));
        return edit;
    }

    private void renderDishes() {
        dishesLayout.removeAllViews();

        LinearLayout categoriesRow = makeRow();

        ArrayList<String> categories = new ArrayList<>();
        categories.add("الكل");

        for (Dish dish : menu.values()) {
            if (!categories.contains(dish.category)) {
                categories.add(dish.category);
            }
        }

        for (String category : categories) {
            Button button = makeButton(category,
                    selectedCategory.equals(category) ? TEAL : NAVY);

            button.setTextSize(11);
            button.setOnClickListener(v -> {
                selectedCategory = category;
                renderDishes();
            });

            LinearLayout.LayoutParams p =
                    new LinearLayout.LayoutParams(0, dp(38), 1);
            p.setMargins(dp(2), dp(2), dp(2), dp(2));
            categoriesRow.addView(button, p);
        }

        add(dishesLayout, categoriesRow, -1, 42);

        LinearLayout currentRow = null;
        int count = 0;

        for (Dish dish : new ArrayList<>(menu.values())) {
            if (!selectedCategory.equals("الكل")
                    && !selectedCategory.equals(dish.category)) {
                continue;
            }

            if (count % 3 == 0) {
                currentRow = makeRow();
                add(dishesLayout, currentRow, -1, 98);
            }

            LinearLayout tile = makeColumn();
            tile.setGravity(Gravity.CENTER);
            tile.setBackground(background(NAVY, 12));

            add(tile, makeText(dish.name, 13, WHITE), -1, 42);
            add(tile, makeText(money(dish.price), 12, ORANGE), -1, 30);

            tile.setOnClickListener(v -> {
                cart.put(dish.name,
                        cart.containsKey(dish.name)
                                ? cart.get(dish.name) + 1 : 1);
                refreshCart();
            });

            tile.setOnLongClickListener(v -> {
                showDishDialog(dish);
                return true;
            });

            LinearLayout.LayoutParams tileParams =
                    new LinearLayout.LayoutParams(0, dp(90), 1);
            tileParams.setMargins(dp(3), dp(3), dp(3), dp(3));
            currentRow.addView(tile, tileParams);

            count++;
        }
    }

    private String money(int amount) {
        return String.format(Locale.US, "%,d د.ع", amount);
    }

    private int getTotal() {
        int total = 0;

        for (Map.Entry<String, Integer> entry : cart.entrySet()) {
            Dish dish = menu.get(entry.getKey());

            if (dish != null) {
                total += dish.price * entry.getValue();
            }
        }

        return total;
    }

    private void refreshCart() {
        if (cartLayout == null) return;

        cartLayout.removeAllViews();

        ArrayList<String> names = new ArrayList<>(cart.keySet());

        for (String name : names) {
            Dish dish = menu.get(name);

            if (dish == null) {
                cart.remove(name);
                continue;
            }

            LinearLayout item = makeRow();

            TextView label = makeText(
                    name + " × " + cart.get(name), 12, WHITE);
            item.addView(label, new LinearLayout.LayoutParams(
                    0, dp(38), 1));

            Button minus = makeButton("−", RED);
            minus.setOnClickListener(v -> {
                int quantity = cart.get(name);

                if (quantity <= 1) {
                    cart.remove(name);
                } else {
                    cart.put(name, quantity - 1);
                }

                refreshCart();
            });

            add(item, minus, 38, 34);
            add(cartLayout, item, -1, 38);
        }

        totalView.setText("المجموع: " + money(getTotal()));
        orderNumberView.setText("رقم الطلب: " + nextOrderNumber);
    }

    private void showDishDialog(Dish existing) {
        LinearLayout form = makeColumn();
        form.setPadding(dp(12), dp(8), dp(12), dp(8));

        EditText name = makeEdit("اسم الطبق");
        EditText category = makeEdit("القسم");
        EditText price = makeEdit("السعر بالدينار");

        price.setInputType(
                android.text.InputType.TYPE_CLASS_NUMBER);

        if (existing != null) {
            name.setText(existing.name);
            category.setText(existing.category);
            price.setText(String.valueOf(existing.price));
        }

        add(form, name, -1, 48);
        add(form, category, -1, 48);
        add(form, price, -1, 48);

        AlertDialog.Builder builder =
                new AlertDialog.Builder(this)
                        .setTitle(existing == null
                                ? "إضافة طبق جديد" : "تعديل الطبق")
                        .setView(form)
                        .setNegativeButton("إلغاء", null)
                        .setPositiveButton("حفظ", null);

        if (existing != null) {
            builder.setNeutralButton("حذف الطبق", null);
        }

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> {
                        String newName =
                                name.getText().toString().trim();
                        String newCategory =
                                category.getText().toString().trim();

                        int newPrice;

                        try {
                            newPrice = Integer.parseInt(
                                    price.getText().toString().trim());
                        } catch (Exception e) {
                            toast("أدخل السعر بصورة صحيحة");
                            return;
                        }

                        if (newName.isEmpty()
                                || newCategory.isEmpty()
                                || newPrice < 0) {
                            toast("أكمل الاسم والقسم والسعر");
                            return;
                        }

                        if (existing != null
                                && !existing.name.equals(newName)
                                && menu.containsKey(newName)) {
                            toast("اسم الطبق موجود مسبقاً");
                            return;
                        }

                        if (existing != null) {
                            menu.remove(existing.name);

                            if (cart.containsKey(existing.name)) {
                                int quantity = cart.remove(existing.name);
                                cart.put(newName, quantity);
                            }
                        }

                        menu.put(newName,
                                new Dish(newName, newCategory, newPrice));

                        saveMenu();
                        renderDishes();
                        refreshCart();
                        dialog.dismiss();
                    });

            if (existing != null) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                        .setOnClickListener(v -> {
                            new AlertDialog.Builder(this)
                                    .setTitle("حذف الطبق")
                                    .setMessage(
                                            "راح ينحذف الطبق من المنيو. الطلبات القديمة تبقى محفوظة.")
                                    .setNegativeButton("إلغاء", null)
                                    .setPositiveButton("حذف", (d, w) -> {
                                        menu.remove(existing.name);
                                        cart.remove(existing.name);
                                        saveMenu();
                                        renderDishes();
                                        refreshCart();
                                        dialog.dismiss();
                                    })
                                    .show();
                });
            }
        });

        dialog.show();
    }

    private void requestCallerRole() {
        if (Build.VERSION.SDK_INT < 29) {
            toast("ميزة تعريف المتصل تحتاج أندرويد 10 أو أحدث");
            return;
        }

        RoleManager manager =
                (RoleManager) getSystemService(Context.ROLE_SERVICE);

        if (manager == null
                || !manager.isRoleAvailable(
                        RoleManager.ROLE_CALL_SCREENING)) {
            toast("ميزة تعريف المتصل غير متاحة بهذا الجهاز");
            return;
        }

        if (manager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            toast("تعريف المتصل مفعّل");
            return;
        }

        Intent intent =
                manager.createRequestRoleIntent(
                        RoleManager.ROLE_CALL_SCREENING);

        startActivityForResult(intent, REQUEST_CALL_ROLE);
    }

    private final Runnable callerUpdater = new Runnable() {
        @Override
        public void run() {
            refreshCaller();
            handler.postDelayed(this, 1000);
        }
    };

    private void refreshCaller() {
        if (prefs == null || customerNumber == null) return;

        String number = prefs.getString("latest_caller", "");
        long time = prefs.getLong("caller_time", 0);

        if (number == null || number.trim().isEmpty()) return;

        if (System.currentTimeMillis() - time > 5 * 60 * 1000) {
            return;
        }

        if (!number.equals(lastCaller)) {
            lastCaller = number;
            customerNumber.setText(number);
            statusView.setText("رقم متصل حديثاً: " + number);
        }
    }

    private JSONArray getOrders() {
        return readArray("orders");
    }

    private void saveAndPrint() {
        if (cart.isEmpty()) {
            toast("أضف طبقاً واحداً على الأقل");
            return;
        }

        try {
            JSONObject order = new JSONObject();

            order.put("number", nextOrderNumber);
            order.put("date", now("yyyy-MM-dd"));
            order.put("time", now("HH:mm:ss"));
            order.put("customer",
                    customerNumber.getText().toString().trim());
            order.put("address",
                    customerAddress.getText().toString().trim());
            order.put("total", getTotal());

            JSONArray items = new JSONArray();

            for (Map.Entry<String, Integer> entry : cart.entrySet()) {
                Dish dish = menu.get(entry.getKey());
                if (dish == null) continue;

                JSONObject item = new JSONObject();
                item.put("name", dish.name);
                item.put("category", dish.category);
                item.put("price", dish.price);
                item.put("quantity", entry.getValue());

                items.put(item);
            }

            order.put("items", items);

            JSONArray orders = getOrders();
            orders.put(order);

            // نحفظ الطلب أولاً قبل فتح نافذة الطباعة
            int followingNumber = nextOrderNumber + 1;

            prefs.edit()
                    .putString("orders", orders.toString())
                    .putInt("next_order", followingNumber)
                    .apply();

            nextOrderNumber = followingNumber;

            String html = buildReceipt(order);
            printReceipt(html);

            cart.clear();
            customerNumber.setText("");
            customerAddress.setText("");
            lastCaller = "";

            prefs.edit()
                    .remove("latest_caller")
                    .remove("caller_time")
                    .apply();

            refreshCart();
            toast("تم حفظ الطلب محلياً");

        } catch (Exception e) {
            toast("تعذر حفظ الطلب");
        }
    }

    private String escapeHtml(String value) {
        if (value == null) return "";

        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String buildReceipt(JSONObject order) throws Exception {
        StringBuilder html = new StringBuilder();

        html.append("<html><head><meta charset='UTF-8'>")
                .append("<meta name='viewport' content='width=device-width'>")
                .append("<style>")
                .append("@page{margin:3mm}")
                .append("body{font-family:sans-serif;direction:rtl;")
                .append("width:100%;margin:0;font-size:12px;color:#000}")
                .append("h2{text-align:center;font-size:19px}")
                .append("p{margin:5px 0}")
                .append("hr{border:0;border-top:1px dashed #000}")
                .append("table{width:100%;border-collapse:collapse}")
                .append("td{padding:5px 1px;vertical-align:top}")
                .append(".amount{text-align:left;white-space:nowrap}")
                .append("</style></head><body>");

        html.append("<h2>مطعم الحجي</h2><hr>");

        html.append("<p>رقم الطلب: ")
                .append(order.getInt("number"))
                .append("</p>");

        html.append("<p>التاريخ: ")
                .append(escapeHtml(order.getString("date")))
                .append("</p>");

        html.append("<p>الوقت: ")
                .append(escapeHtml(order.getString("time")))
                .append("</p>");

        String customer = order.optString("customer", "");
        if (!customer.isEmpty()) {
            html.append("<p>رقم الزبون: ")
                    .append(escapeHtml(customer))
                    .append("</p>");
        }

        String address = order.optString("address", "");
        if (!address.isEmpty()) {
            html.append("<p>العنوان: ")
                    .append(escapeHtml(address))
                    .append("</p>");
        }

        html.append("<hr><table>");

        JSONArray items = order.getJSONArray("items");

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);

            int quantity = item.getInt("quantity");
            int price = item.getInt("price");

            html.append("<tr><td>")
                    .append(escapeHtml(item.getString("name")))
                    .append(" × ")
                    .append(quantity)
                    .append("</td><td class='amount'>")
                    .append(String.format(
                            Locale.US, "%,d", price * quantity))
                    .append("</td></tr>");
        }

        html.append("</table><hr>")
                .append("<h3>المجموع: ")
                .append(String.format(
                        Locale.US, "%,d", order.getInt("total")))
                .append(" د.ع</h3>")
                .append("<p style='text-align:center'>أهلاً وسهلاً بكم</p>")
                .append("</body></html>");

        return html.toString();
    }

    private void printReceipt(String html) {
        if (printWebView != null) {
            printWebView.destroy();
        }

        printWebView = new WebView(this);

        printWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                PrintManager manager = (PrintManager)
                        getSystemService(Context.PRINT_SERVICE);

                if (manager != null) {
                    PrintAttributes attributes =
                            new PrintAttributes.Builder()
                                    .setColorMode(
                                            PrintAttributes.COLOR_MODE_MONOCHROME)
                                    .build();

                    manager.print(
                            "فاتورة مطعم الحجي",
                            view.createPrintDocumentAdapter(
                                    "فاتورة مطعم الحجي"),
                            attributes
                    );
                }
            }
        });

        printWebView.loadDataWithBaseURL(
                null, html, "text/html", "UTF-8", null);
    }

    private void exportOrders() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE,
                "haji-orders-" + now("yyyy-MM-dd") + ".csv");

        startActivityForResult(intent, REQUEST_EXPORT);
    }

    private String csvCell(String value) {
        if (value == null) value = "";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private void handleExport(Uri uri) {
        try {
            JSONArray orders = getOrders();
            StringBuilder csv = new StringBuilder();

            csv.append(
                    "رقم الطلب,التاريخ,الوقت,رقم الزبون,العنوان,الأطباق,المجموع\n");

            String today = now("yyyy-MM-dd");

            for (int i = 0; i < orders.length(); i++) {
                JSONObject order = orders.getJSONObject(i);

                if (!today.equals(order.optString("date"))) {
                    continue;
                }

                StringBuilder names = new StringBuilder();
                JSONArray items = order.getJSONArray("items");

                for (int j = 0; j < items.length(); j++) {
                    JSONObject item = items.getJSONObject(j);

                    if (j > 0) names.append(" | ");

                    names.append(item.optString("name"))
                            .append(" x")
                            .append(item.optInt("quantity"));
                }

                csv.append(order.optInt("number")).append(',')
                        .append(csvCell(order.optString("date"))).append(',')
                        .append(csvCell(order.optString("time"))).append(',')
                        .append(csvCell(order.optString("customer"))).append(',')
                        .append(csvCell(order.optString("address"))).append(',')
                        .append(csvCell(names.toString())).append(',')
                        .append(order.optInt("total")).append('\n');
            }

            try (OutputStream output =
                         getContentResolver().openOutputStream(uri)) {
                if (output == null) {
                    throw new Exception("تعذر فتح الملف");
                }

                output.write(
                        ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8));
                output.flush();
            }

            toast("تم تصدير حسابات اليوم");

        } catch (Exception e) {
            toast("فشل تصدير الحسابات");
        }
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle("تصفير حسابات اليوم")
                .setMessage(
                        "سيتم حذف الطلبات المحفوظة من هذا الجهاز. صدّر الحسابات قبل المتابعة.")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("تصفير", (dialog, which) -> {
                    prefs.edit()
                            .putString("orders", "[]")
                            .putInt("next_order", 1)
                            .remove("latest_caller")
                            .remove("caller_time")
                            .apply();

                    nextOrderNumber = 1;
                    cart.clear();
                    customerNumber.setText("");
                    customerAddress.setText("");
                    lastCaller = "";

                    refreshCart();
                    toast("تم تصفير الحسابات");
                })
                .show();
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_EXPORT
                && resultCode == RESULT_OK
                && data != null
                && data.getData() != null) {

            handleExport(data.getData());
        }

        if (requestCode == REQUEST_CALL_ROLE) {
            if (Build.VERSION.SDK_INT >= 29) {
                RoleManager manager =
                        (RoleManager) getSystemService(Context.ROLE_SERVICE);

                if (manager != null
                        && manager.isRoleHeld(
                                RoleManager.ROLE_CALL_SCREENING)) {
                    toast("تم تفعيل تعريف المتصل");
                } else {
                    toast("لم يتم تفعيل تعريف المتصل");
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (prefs != null) {
            nextOrderNumber = prefs.getInt("next_order", 1);
        }

        refreshCart();
        refreshCaller();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(callerUpdater);

        if (printWebView != null) {
            printWebView.destroy();
            printWebView = null;
        }

        super.onDestroy();
    }
}
