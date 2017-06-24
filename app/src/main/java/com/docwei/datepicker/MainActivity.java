package com.docwei.datepicker;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final int REQUEST_SINGLE_DATE = 1;
    private static final int REQUEST_MULTIPLE_DATE = 2;
    private TextView mTv1;
    private TextView mTv2;
    private Button mBtn1;
    private Button mBtn2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
    }

    private void initView() {
        mTv1 = (TextView) findViewById(R.id.tv1);
        mTv2 = (TextView) findViewById(R.id.tv2);
        mBtn1 = (Button) findViewById(R.id.btn_1);
        mBtn2 = (Button) findViewById(R.id.btn_2);
        mBtn1.setOnClickListener(this);
        mBtn2.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.btn_1:
                //选择一个日期，以mTv1的日期作为初始选择的日期
                String dateStr=mTv1.getText().toString();
                Intent intent=new Intent(MainActivity.this,SampleTimesSquareActivity.class);
                intent.putExtra("DATE",dateStr);
                startActivityForResult(intent,REQUEST_SINGLE_DATE);
                break;
            case R.id.btn_2:
                //选择开始结束日期，以mTv2的开始结束日期作为初始选择的日期
                //选择一个日期，以mTv1的日期作为初始选择的日期
                String dateStr2=mTv2.getText().toString();
                Intent intent2=new Intent(MainActivity.this,SampleTimesSquareActivity.class);
                intent2.putExtra("TWODATE",dateStr2);
                System.out.println("发送datestr2------"+dateStr2);
                startActivityForResult(intent2,REQUEST_MULTIPLE_DATE);
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data != null) {
            if(requestCode==REQUEST_SINGLE_DATE){
                String singleDate = data.getStringExtra("SINGLEDATE");
               mTv1.setText(singleDate);
            }else if(requestCode==REQUEST_MULTIPLE_DATE){
                String multipleDate = data.getStringExtra("MULTIPLEDATE");
                mTv2.setText(multipleDate);
            }
        }

    }
}
