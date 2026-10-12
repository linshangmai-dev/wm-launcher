package com.yftech.vehiclecenter.ui.activites;
public class EnergyActivity extends android.app.Activity {
    @Override public void onCreate(android.os.Bundle saved) {
        super.onCreate(saved);
        android.widget.TextView text = new android.widget.TextView(this);
        text.setText("能量管理测试页"); text.setTextSize(32); text.setGravity(17); setContentView(text);
    }
}
