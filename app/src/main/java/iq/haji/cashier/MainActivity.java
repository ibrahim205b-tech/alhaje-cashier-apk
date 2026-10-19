package iq.haji.cashier;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private final int RED = Color.rgb(202, 35, 48);
    private final int DARK = Color.rgb(38, 39, 44);
    private final int BG = Color.rgb(247, 247, 249);
    private final int WHITE = Color.WHITE;

    private LinearLayout root, cart;
    private TextView totalView;
    private SharedPreferences prefs;
    private JSONArray items = new JSONArray();
    private int total = 0;
    private int orderNumber = 1;

    private final String[] dishes = {
        "برغر", "بيتزا", "كباب", "وجبة دجاج",
        "شاورما", "بطاطا", "بيبسي", "ماء"
    };

    private final int[] prices = {
        5000, 8000, 6000, 7000,
        4000, 2000, 500, 250
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("alhaje", MODE_PRIVATE);
        orderNumber = prefs.getInt("orderNumber", 1);

        showHome();
    }

    private GradientDrawable bg(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(20);
        return d;
    }

    private TextView label(String s, int size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(null, Typeface.BOLD);
        t.setPadding(10, 10, 10, 10);
        return t;
    }

    private Button makeButton(String s, int color) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(WHITE);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setBackground(bg(color));
        return b;
    }

    private LinearLayout linear(int orientation) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(orientation);
        return l;
    }

    private void showHome() {
        root = linear(LinearLayout.VERTICAL);
        root.setPadding(10, 10, 10, 10);
        root.setBackgroundColor(BG);
        setContentView(root);

        LinearLayout header = linear(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackground(bg(RED));

        TextView title = label("🍕 دليفري الحجي", 26, WHITE);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        Button report = makeButton("التقارير", DARK);
        report.setOnClickListener(v -> showReports());
        header.addView(report);

        root.addView(header);

        LinearLayout body = linear(LinearLayout.HORIZONTAL);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));

        ScrollView menuScroll = new ScrollView(this);
        LinearLayout menu = linear(LinearLayout.VERTICAL);
        menuScroll.addView(menu);

        body.addView(menuScroll, new LinearLayout.LayoutParams(0, -1, 1.2f));

        ScrollView cartScroll = new ScrollView(this);
        cart = linear(LinearLayout.VERTICAL);
        cart.setBackground(bg(WHITE));
        cartScroll.addView(cart);

        body.addView(cartScroll, new LinearLayout.LayoutParams(0, -1, 1));

        for (int i = 0; i < dishes.length; i++) {
            final int index = i;
            Button b = makeButton(
                dishes[i] + "\n" +
                String.format(Locale.US, "%,d د.ع", prices[i]),
                RED
            );

            LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(-1, 90);
            p.setMargins(5, 5, 5, 5);
            menu.addView(b, p);
            b.setOnClickListener(v -> addItem(index));
        }

        refreshCart();
    }

    private void addItem(int index) {
        try {
            JSONObject item = new JSONObject();
            item.put("name", dishes[index]);
            item.put("price", prices[index]);
            item.put("qty", 1);
            items.put(item);
            refreshCart();
        } catch (Exception ignored) {}
    }

    private void refreshCart() {
        cart.removeAllViews();
        total = 0;

        cart.addView(label("الطلب الحالي #" + orderNumber, 22, RED));

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;

            total += item.optInt("price") * item.optInt("qty", 1);

            cart.addView(label(
                item.optString("name") + " × " + item.optInt("qty", 1),
                17, DARK
            ));
        }

        totalView = label(
            "المجموع: " + String.format(Locale.US, "%,d", total) + " د.ع",
            22, RED
        );
        cart.addView(totalView);

        Button save = makeButton("حفظ الطلب", RED);
        cart.addView(save);
        save.setOnClickListener(v -> saveOrder());

        Button clear = makeButton("تفريغ الطلب", DARK);
        cart.addView(clear);
        clear.setOnClickListener(v -> {
            items = new JSONArray();
            refreshCart();
        });
    }

    private void saveOrder() {
        if (items.length() == 0) {
            Toast.makeText(this, "أضف أصنافاً أولاً", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String old = prefs.getString("orders", "[]");
            JSONArray orders = new JSONArray(old);

            JSONObject order = new JSONObject();
            order.put("number", orderNumber);
            order.put("date", new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", Locale.getDefault()
            ).format(new Date()));
            order.put("items", items);
            order.put("total", total);

            orders.put(order);
            orderNumber++;

            prefs.edit()
                .putString("orders", orders.toString())
                .putInt("orderNumber", orderNumber)
                .apply();

            items = new JSONArray();
            showHome();

            Toast.makeText(this, "تم حفظ الطلب", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this, "تعذر حفظ الطلب", Toast.LENGTH_SHORT).show();
        }
    }

    private void showReports() {
        String data = prefs.getString("orders", "[]");

        try {
            JSONArray orders = new JSONArray(data);
            int sum = 0;

            for (int i = 0; i < orders.length(); i++) {
                sum += orders.getJSONObject(i).optInt("total");
            }

            new android.app.AlertDialog.Builder(this)
                .setTitle("تقارير دليفري الحجي")
                .setMessage(
                    "عدد الطلبات: " + orders.length() +
                    "\nإجمالي المبيعات: " +
                    String.format(Locale.US, "%,d", sum) + " د.ع"
                )
                .setPositiveButton("رجوع", (d, w) -> {})
                .show();

        } catch (Exception e) {
            Toast.makeText(this, "تعذر عرض التقارير", Toast.LENGTH_SHORT).show();
        }
    }
}
