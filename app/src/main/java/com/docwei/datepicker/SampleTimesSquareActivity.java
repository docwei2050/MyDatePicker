package com.docwei.datepicker;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.docwei.datepicker.timesquare.CalendarCellDecorator;
import com.docwei.datepicker.timesquare.CalendarPickerView;
import com.docwei.datepicker.timesquare.DefaultDayViewAdapter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class SampleTimesSquareActivity extends AppCompatActivity {

    private CalendarPickerView mPickerview;
    private SimpleDateFormat mSimpleDateFormat;
    private Button mBtn_today;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_timesquare);

        Toolbar toolbar= (Toolbar) findViewById(R.id.tb);
        setSupportActionBar(toolbar);

        final Calendar nextYear = Calendar.getInstance();
        nextYear.add(Calendar.YEAR, 1);

        final Calendar lastYear = Calendar.getInstance();
        lastYear.add(Calendar.YEAR, -1);

        mPickerview = (CalendarPickerView) findViewById(R.id.calendar_view);
        mPickerview.setCustomDayView(new DefaultDayViewAdapter());
        mPickerview.setDecorators(Collections.<CalendarCellDecorator>emptyList());

        mSimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");


        mPickerview.setOnSingleAndMutipleChoiceListener(new CalendarPickerView.OnSingleAndMutipleChoiceListener() {
            @Override
            public void onSingleChoice(Date date) {
                Intent intent = new Intent();
                intent.putExtra("SINGLEDATE", mSimpleDateFormat.format(date));
                setResult(RESULT_OK, intent);
                finish();

            }

            @Override
            public void onMutipleChoice(Date startDate, Date endDate) {
                Intent intent = new Intent();
                intent.putExtra("MULTIPLEDATE", mSimpleDateFormat.format(startDate) + "至" + mSimpleDateFormat.format(endDate));
                setResult(RESULT_OK, intent);
                finish();
            }
        });
        mBtn_today= (Button) findViewById(R.id.title_right);
        mBtn_today.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPickerview.scrollToDate(new Date());
            }
        });



        initData(nextYear, lastYear);

    }

    private void initData(Calendar nextYear, Calendar lastYear) {
        Intent intent = getIntent();
        String dateStr = intent.getStringExtra("DATE");
        String dateStr2 = intent.getStringExtra("TWODATE");
        if (!TextUtils.isEmpty(dateStr)) {
            Date date;
            try {
                date = mSimpleDateFormat.parse(dateStr);
            } catch (ParseException e) {
                e.printStackTrace();
                date = new Date();
            }
            mPickerview.init(lastYear.getTime(), nextYear.getTime()) //
                    .inMode(CalendarPickerView.SelectionMode.SINGLE) //
                    .withSelectedDate(date);
        }
        if (!TextUtils.isEmpty(dateStr2)) {
            System.out.println(dateStr2);
            String[] dateStrs = dateStr2.split("至");
            List<Date> list = new ArrayList<>();
            Date date1, date2;
            try {
                date1 = mSimpleDateFormat.parse(dateStrs[0]);
                date2 = mSimpleDateFormat.parse(dateStrs[1]);
                list.add(date1);
                list.add(date2);
            } catch (ParseException e) {
                e.printStackTrace();
                Toast.makeText(SampleTimesSquareActivity.this, "日期不选择", Toast.LENGTH_SHORT).show();
                mPickerview.init(lastYear.getTime(), nextYear.getTime()) //
                        .inMode(CalendarPickerView.SelectionMode.MULTIPLE) //
                        .withSelectedDates(null);
            }
            mPickerview.init(lastYear.getTime(), nextYear.getTime()) //
                    .inMode(CalendarPickerView.SelectionMode.MULTIPLE) //
                    .withSelectedDates(list);
        }

    }


}