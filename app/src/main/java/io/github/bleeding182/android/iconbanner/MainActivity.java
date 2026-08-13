package io.github.bleeding182.android.iconbanner;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Exists so the demo has a launcher entry to look at. With five flavors installed at once the icon on
 * the launcher is the point, but opening one should still say which one it is.
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView text = new TextView(this);
        text.setText(getString(R.string.app_name) + "\n" + getPackageName());
        text.setPadding(48, 48, 48, 48);
        setContentView(text);
    }
}
