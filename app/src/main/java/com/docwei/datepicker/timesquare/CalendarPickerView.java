package com.docwei.datepicker.timesquare;


import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.docwei.datepicker.R;

import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import static java.util.Calendar.DATE;
import static java.util.Calendar.DAY_OF_MONTH;
import static java.util.Calendar.DAY_OF_WEEK;
import static java.util.Calendar.HOUR_OF_DAY;
import static java.util.Calendar.MILLISECOND;
import static java.util.Calendar.MINUTE;
import static java.util.Calendar.MONTH;
import static java.util.Calendar.SECOND;
import static java.util.Calendar.YEAR;

/**
 * Android component to allow picking a date from a calendar view (a list of months).  Must be
 * initialized after inflation with {@link #init(Date, Date)} and can be customized with any of the
 * {@link FluentInitializer} methods returned.  The currently selected date can be retrieved with
 * {@link #getSelectedDate()}.
 */
public class CalendarPickerView extends ListView {
  public enum SelectionMode {
    /**
     * Only one date will be selectable.  If there is already a selected date and you select a new
     * one, the old date will be unselected.
     */
    SINGLE,
    /** Multiple dates will be selectable.  Selecting an already-selected date will un-select it. */
    MULTIPLE,
    /**
     * Allows you to select a date range.  Previous selections are cleared when you either:
     * <ul>
     * <li>Have a range selected and select another date (even if it's in the current range).</li>
     * <li>Have one date selected and then select an earlier date.</li>
     * </ul>
     */
    RANGE
  }

  private final CalendarPickerView.MonthAdapter adapter;
  private final IndexedLinkedHashMap<String, List<List<MonthCellDescriptor>>> cells =
          new IndexedLinkedHashMap<>();
  final MonthView.Listener listener = new CellClickedListener();
  final List<MonthDescriptor> months = new ArrayList<>();
  final List<MonthCellDescriptor> selectedCells = new ArrayList<>();
  final List<Calendar> selectedCals = new ArrayList<>();
  private Locale locale;
  private TimeZone timeZone;
  private DateFormat monthNameFormat;
  private DateFormat weekdayNameFormat;
  private DateFormat fullDateFormat;
  private Calendar minCal;
  private Calendar maxCal;
  private Calendar monthCounter;
  private boolean displayOnly;
  SelectionMode selectionMode;
  Calendar today;
  private int dividerColor;
  private int dayBackgroundResId;
  private int dayTextColorResId;
  private int titleTextColor;
  private boolean displayHeader;
  private int headerTextColor;
  private Typeface titleTypeface;
  private Typeface dateTypeface;
  private List<MonthCellDescriptor> mCells=new ArrayList<>();
  private OnDateSelectedListener dateListener;
  private DateSelectableFilter dateConfiguredListener;
  private OnInvalidDateSelectedListener invalidDateListener =
          new DefaultOnInvalidDateSelectedListener();
  private CellClickInterceptor cellClickInterceptor;
  private List<CalendarCellDecorator> decorators;
  private DayViewAdapter dayViewAdapter = new DefaultDayViewAdapter();

  public void setDecorators(List<CalendarCellDecorator> decorators) {
    this.decorators = decorators;
    if (null != adapter) {
      adapter.notifyDataSetChanged();
    }
  }

  public List<CalendarCellDecorator> getDecorators() {
    return decorators;
  }

  public CalendarPickerView(Context context, AttributeSet attrs) {
    super(context, attrs);

    Resources res = context.getResources();
    TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CalendarPickerView);
    final int bg = a.getColor(R.styleable.CalendarPickerView_android_background,
            res.getColor(R.color.calendar_bg));
    dividerColor = a.getColor(R.styleable.CalendarPickerView_tsquare_dividerColor,
            res.getColor(R.color.calendar_divider));
    dayBackgroundResId = a.getResourceId(R.styleable.CalendarPickerView_tsquare_dayBackground,
            R.drawable.calendar_bg_selector);
    dayTextColorResId = a.getResourceId(R.styleable.CalendarPickerView_tsquare_dayTextColor,
            R.color.calendar_text_selector);
    titleTextColor = a.getColor(R.styleable.CalendarPickerView_tsquare_titleTextColor,Color.WHITE);
    displayHeader = a.getBoolean(R.styleable.CalendarPickerView_tsquare_displayHeader, false);
    headerTextColor = a.getColor(R.styleable.CalendarPickerView_tsquare_headerTextColor,
            res.getColor(R.color.calendar_text_active));
    a.recycle();

    adapter = new MonthAdapter();
    setDivider(null);
    setDividerHeight(0);
    setBackgroundColor(bg);
    setCacheColorHint(bg);
    timeZone = TimeZone.getDefault();
    locale = Locale.getDefault();
    today = Calendar.getInstance(timeZone, locale);
    minCal = Calendar.getInstance(timeZone, locale);
    maxCal = Calendar.getInstance(timeZone, locale);
    monthCounter = Calendar.getInstance(timeZone, locale);
    monthNameFormat = new SimpleDateFormat(context.getString(R.string.month_name_format), locale);
    monthNameFormat.setTimeZone(timeZone);
    weekdayNameFormat = new SimpleDateFormat(context.getString(R.string.day_name_format), locale);
    weekdayNameFormat.setTimeZone(timeZone);
    fullDateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, locale);
    fullDateFormat.setTimeZone(timeZone);

    if (isInEditMode()) {
      Calendar nextYear = Calendar.getInstance(timeZone, locale);
      nextYear.add(YEAR, 1);

      init(new Date(), nextYear.getTime()) //
              .withSelectedDate(new Date());
    }
  }

  public FluentInitializer init(Date minDate, Date maxDate, TimeZone timeZone, Locale locale) {
    if (minDate == null || maxDate == null) {
      throw new IllegalArgumentException(
              "minDate and maxDate must be non-null.  " + dbg(minDate, maxDate));
    }
    if (minDate.after(maxDate)) {
      throw new IllegalArgumentException(
              "minDate must be before maxDate.  " + dbg(minDate, maxDate));
    }
    if (locale == null) {
      throw new IllegalArgumentException("Locale is null.");
    }
    if (timeZone == null) {
      throw new IllegalArgumentException("Time zone is null.");
    }

    // Make sure that all calendar instances use the same time zone and locale.
    this.timeZone = timeZone;
    this.locale = locale;
    today = Calendar.getInstance(timeZone, locale);
    minCal = Calendar.getInstance(timeZone, locale);
    maxCal = Calendar.getInstance(timeZone, locale);
    monthCounter = Calendar.getInstance(timeZone, locale);
    monthNameFormat =
            new SimpleDateFormat(getContext().getString(R.string.month_name_format), locale);
    monthNameFormat.setTimeZone(timeZone);
    for (MonthDescriptor month : months) {
         month.setLabel(monthNameFormat.format(month.getDate()));

    }
    weekdayNameFormat =
            new SimpleDateFormat(getContext().getString(R.string.day_name_format), locale);
    weekdayNameFormat.setTimeZone(timeZone);
    fullDateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, locale);
    fullDateFormat.setTimeZone(timeZone);

    this.selectionMode = SelectionMode.SINGLE;
    // Clear out any previously-selected dates/cells.
    selectedCals.clear();
    selectedCells.clear();


    // Clear previous state.
    cells.clear();
    months.clear();
    minCal.setTime(minDate);
    maxCal.setTime(maxDate);
    setMidnight(minCal);
    setMidnight(maxCal);
    displayOnly = false;

    // maxDate is exclusive: bump back to the previous day so if maxDate is the first of a month,
    // we don't accidentally include that month in the view.
    maxCal.add(MINUTE, -1);

    // Now iterate between minCal and maxCal and build up our list of months to show.
    monthCounter.setTime(minCal.getTime());
    final int maxMonth = maxCal.get(MONTH);
    final int maxYear = maxCal.get(YEAR);
    //把近两年的日期全部加入集合
    while ((monthCounter.get(MONTH) <= maxMonth // Up to, including the month.
            || monthCounter.get(YEAR) < maxYear) // Up to the year.
            && monthCounter.get(YEAR) < maxYear + 1) { // But not > next yr.
      Date date = monthCounter.getTime();
      MonthDescriptor month =
              new MonthDescriptor(monthCounter.get(MONTH), monthCounter.get(YEAR), date,
                      monthNameFormat.format(date));
      cells.put(monthKey(month), getMonthCells(month, monthCounter));
      Logr.d("Adding month %s", month);
      months.add(month);
      monthCounter.add(MONTH, 1);
    }

    validateAndUpdate();
    return new FluentInitializer();
  }
  public FluentInitializer init(Date minDate, Date maxDate) {
    return init(minDate, maxDate, TimeZone.getDefault(), Locale.getDefault());
  }


  public FluentInitializer init(Date minDate, Date maxDate, Locale locale) {
    return init(minDate, maxDate, TimeZone.getDefault(), locale);
  }

  public class FluentInitializer {
    /** Override the {@link SelectionMode} from the default ({@link SelectionMode#SINGLE}). */
    public FluentInitializer inMode(SelectionMode mode) {
      selectionMode = mode;
      validateAndUpdate();
      return this;
    }

    /**
     * Set an initially-selected date.  The calendar will scroll to that date if it's not already
     * visible.
     */
    public FluentInitializer withSelectedDate(Date selectedDates) {
      return withSelectedDates(Collections.singletonList(selectedDates));
    }

    /**
     * Set multiple selected dates.  This will throw an {@link IllegalArgumentException} if you
     * pass in multiple dates and haven't already called {@link #inMode(SelectionMode)}.
     */
    public FluentInitializer withSelectedDates(Collection<Date> selectedDates) {
      if (selectionMode == SelectionMode.SINGLE && selectedDates.size() > 1) {
        throw new IllegalArgumentException("SINGLE mode can't be used with multiple selectedDates");
      }
      if (selectionMode == SelectionMode.RANGE && selectedDates.size() > 2) {
        throw new IllegalArgumentException(
                "RANGE mode only allows two selectedDates.  You tried to pass " + selectedDates.size());
      }
      //我们使用双选的话，选择的日期最多也是0-2；

      if (selectedDates != null) {
        for (Date date : selectedDates) {
          selectDate(date,false);
        }
      }
      scrollToSelectedDates();

      validateAndUpdate();
      return this;
    }

    @SuppressLint("SimpleDateFormat")
    public FluentInitializer setShortWeekdays(String[] newShortWeekdays) {
      DateFormatSymbols symbols = new DateFormatSymbols(locale);
      symbols.setShortWeekdays(newShortWeekdays);
      weekdayNameFormat =
              new SimpleDateFormat(getContext().getString(R.string.day_name_format), symbols);
      return this;
    }

    public FluentInitializer displayOnly() {
      displayOnly = true;
      return this;
    }
  }

  private void validateAndUpdate() {
    if (getAdapter() == null) {
      setAdapter(adapter);
    }
    adapter.notifyDataSetChanged();
  }

  private void scrollToSelectedMonth(final int selectedIndex) {
    scrollToSelectedMonth(selectedIndex, false);
  }

  private void scrollToSelectedMonth(final int selectedIndex, final boolean smoothScroll) {
    post(new Runnable() {
      @Override public void run() {
        Logr.d("Scrolling to position %d", selectedIndex);

        if (smoothScroll) {
          smoothScrollToPosition(selectedIndex);
        } else {
          setSelection(selectedIndex);
        }
      }
    });
  }
  /* 滚到选择的日期*/
  private void scrollToSelectedDates() {
    Integer selectedIndex = null;
    Integer todayIndex = null;
    Calendar today = Calendar.getInstance(timeZone, locale);
    for (int c = 0; c < months.size(); c++) {
      MonthDescriptor month = months.get(c);
      if (selectedIndex == null) {
        for (Calendar selectedCal : selectedCals) {
          if (sameMonth(selectedCal, month)) {
            selectedIndex = c;
            break;
          }
        }
        if (selectedIndex == null && todayIndex == null && sameMonth(today, month)) {
          todayIndex = c;
        }
      }
    }
    if (selectedIndex != null) {
      scrollToSelectedMonth(selectedIndex);
    } else if (todayIndex != null) {
      scrollToSelectedMonth(todayIndex);
    }
  }

  public boolean scrollToDate(Date date) {
    Integer selectedIndex = null;

    Calendar cal = Calendar.getInstance(timeZone, locale);
    cal.setTime(date);
    for (int c = 0; c < months.size(); c++) {
      MonthDescriptor month = months.get(c);
      if (sameMonth(cal, month)) {
        selectedIndex = c;
        break;
      }
    }
    if (selectedIndex != null) {
      scrollToSelectedMonth(selectedIndex);
      return true;
    }
    return false;
  }

  /**
   * This method should only be called if the calendar is contained in a dialog, and it should only
   * be called once, right after the dialog is shown (using
   * {@link android.content.DialogInterface.OnShowListener} or
   * {@link android.app.DialogFragment#onStart()}).
   */
  public void fixDialogDimens() {
    Logr.d("Fixing dimensions to h = %d / w = %d", getMeasuredHeight(), getMeasuredWidth());
    // Fix the layout height/width after the dialog has been shown.
    getLayoutParams().height = getMeasuredHeight();
    getLayoutParams().width = getMeasuredWidth();
    // Post this runnable so it runs _after_ the dimen changes have been applied/re-measured.
    post(new Runnable() {
      @Override public void run() {
        Logr.d("Dimens are fixed: now scroll to the selected date");
        scrollToSelectedDates();
      }
    });
  }

  /**
   * Set the typeface to be used for month titles.
   */
  public void setTitleTypeface(Typeface titleTypeface) {
    this.titleTypeface = titleTypeface;
    validateAndUpdate();
  }

  /**
   * Sets the typeface to be used within the date grid.
   */
  public void setDateTypeface(Typeface dateTypeface) {
    this.dateTypeface = dateTypeface;
    validateAndUpdate();
  }

  /**
   * Sets the typeface to be used for all text within this calendar.
   */
  public void setTypeface(Typeface typeface) {
    setTitleTypeface(typeface);
    setDateTypeface(typeface);
  }

  /**
   * This method should only be called if the calendar is contained in a dialog, and it should only
   * be called when the screen has been rotated and the dialog should be re-measured.
   */
  public void unfixDialogDimens() {
    Logr.d("Reset the fixed dimensions to allow for re-measurement");
    // Fix the layout height/width after the dialog has been shown.
    getLayoutParams().height = LayoutParams.MATCH_PARENT;
    getLayoutParams().width = LayoutParams.MATCH_PARENT;
    requestLayout();
  }

  @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    if (months.isEmpty()) {
      throw new IllegalStateException(
              "Must have at least one month to display.  Did you forget to call init()?");
    }
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }

  public Date getSelectedDate() {
    return (selectedCals.size() > 0 ? selectedCals.get(0).getTime() : null);
  }



  /** Returns a string summarizing what the client sent us for init() params. */
  private static String dbg(Date minDate, Date maxDate) {
    return "minDate: " + minDate + "\nmaxDate: " + maxDate;
  }

  /** Clears out the hours/minutes/seconds/millis of a Calendar. */
  static void setMidnight(Calendar cal) {
    cal.set(HOUR_OF_DAY, 0);
    cal.set(MINUTE, 0);
    cal.set(SECOND, 0);
    cal.set(MILLISECOND, 0);
  }

  private class CellClickedListener implements MonthView.Listener {
    @Override public void handleClick(MonthCellDescriptor cell) {
      if(selectionMode==SelectionMode.SINGLE){
        //什么都不用做
      }else if(selectionMode==SelectionMode.MULTIPLE){
        mutipleChoice(cell);
      }

      //后面会加进去的
      Date clickedDate = cell.getDate();

      if (cellClickInterceptor != null && cellClickInterceptor.onCellClicked(clickedDate)) {
        return;
      }

      if (!betweenDates(clickedDate, minCal, maxCal) || !isDateSelectable(clickedDate)) {
        if (invalidDateListener != null) {
          invalidDateListener.onInvalidDateSelected(clickedDate);
        }
      } else {
        boolean wasSelected = doSelectDate(clickedDate, cell);
        if(mOnSingleAndMutipleChoiceListener!=null){
          if(wasSelected){
            //区分单选和双选
            switch (selectionMode){
              case SINGLE:
                mOnSingleAndMutipleChoiceListener.onSingleChoice(clickedDate);
                break;
              case MULTIPLE:
              if(selectedCells.size()==2){
                mOnSingleAndMutipleChoiceListener.onMutipleChoice(selectedCells.get(0).getDate(), selectedCells.get(1).getDate());
              }
                break;
            }

            validateAndUpdate();
          }
        }

        if (dateListener != null) {
          if (wasSelected) {
            dateListener.onDateSelected(clickedDate);
          } else {
            dateListener.onDateUnselected(clickedDate);
          }
        }
      }
    }
  }
  public void mutipleChoice(MonthCellDescriptor cell) {
    if (selectedCells.size() == 0) {
      cell.setEnd(false);
      cell.setSelected(true);
      cell.setStart(true);
    } else if (selectedCells.size() == 1) {
      //如果之前已经有一个值了，那么就需要判断这个已选的日期跟现在的选择的日期是否匹配
      boolean notMatch = selectedCells.get(0).getDate().before(cell.getDate());
      if (notMatch) {
        cell.setStart(false);
        cell.setSelected(true);
        cell.setEnd(true);
      } else {
        setCellValue();
        cell.setEnd(false);
        cell.setSelected(true);
        cell.setStart(true);
      }
    } else if (selectedCells.size() == 2) {
      setCellValue();

      cell.setEnd(false);
      cell.setSelected(true);
      cell.setStart(true);
    }
  }


  private boolean goTomatchSelected(MonthCellDescriptor nextCell) {
    MonthCellDescriptor cell=selectedCells.get(0);
    return cell.getDate().before(nextCell.getDate());
  }

  private void setCellValue() {
    for(MonthCellDescriptor cell :selectedCells){
      cell.setStart(false);
      cell.setEnd(false);
      cell.setSelected(false);
    }
    selectedCells.clear();
  }


  public boolean selectDate(Date date) {
    return selectDate(date, false);
  }


  public boolean selectDate(Date date, boolean smoothScroll) {
    validateDate(date);

    MonthCellWithMonthIndex monthCellWithMonthIndex = getMonthCellWithIndexByDate(date);
    if (monthCellWithMonthIndex == null || !isDateSelectable(date)) {
      return false;
    }
    boolean wasSelected = doSelectDate(date, monthCellWithMonthIndex.cell);
    if (wasSelected) {
      scrollToSelectedMonth(monthCellWithMonthIndex.monthIndex, smoothScroll);
    }
    return wasSelected;
  }

  private int getDateKey(Calendar calendar) {
    int date=calendar.get(DATE);
    calendar.set(DATE,1);
    return calendar.get(DAY_OF_WEEK)-1+date;
  }

  private int getDiffValue(Calendar minCal, Calendar calendar) {
    int value=0;
    int minYear=minCal.get(YEAR);
    int minMonth=minCal.get(MONTH);
    int currentYear=calendar.get(YEAR);
    int currentMonth=calendar.get(MONTH);

    if(minYear==currentYear){
      value=currentMonth-minMonth;
    }else{
      value=12*(currentYear-minYear)+(currentMonth-minMonth);
    }
    return value;
  }

  //合法化数据
  private void validateDate(Date date) {
    if (date == null) {
      throw new IllegalArgumentException("Selected date must be non-null.");
    }
    if (date.before(minCal.getTime()) || date.after(maxCal.getTime())) {
      throw new IllegalArgumentException(String.format(
              "SelectedDate must be between minDate and maxDate."
                      + "%nminDate: %s%nmaxDate: %s%nselectedDate: %s", minCal.getTime(), maxCal.getTime(),
              date));
    }
  }
   //做选择日期的事件
  private boolean doSelectDate(Date date, MonthCellDescriptor cell) {
    Calendar newlySelectedCal = Calendar.getInstance(timeZone, locale);

    newlySelectedCal.setTime(date);
    // Sanitize input: clear out the hours/minutes/seconds/millis.
    setMidnight(newlySelectedCal);


    switch (selectionMode) {
      case SINGLE:
        clearOldSelections();
        if(date!=null){
          cell.setSelected(true);
          selectedCells.add(cell);
          selectedCals.add(newlySelectedCal);
        }
        break;
      case MULTIPLE:
      /*  date = applyMultiSelect(date, newlySelectedCal);*/
        if (date != null) {
          // Select a new cell.
          if (selectedCells.size() == 0) {
            cell.setSelected(true);
            cell.setStart(true);
            selectedCells.add(cell);
          }else if(selectedCells.size() == 1){
            cell.setSelected(true);
            cell.setEnd(true);
            selectedCells.add(cell);
          }
          selectedCals.add(newlySelectedCal);
        }
        break;
      default:
        throw new IllegalStateException("Unknown selectionMode " + selectionMode);
    }


    // Update the adapter.
    validateAndUpdate();
    return date != null;
  }

  private String monthKey(Calendar cal) {
    return cal.get(YEAR) + "-" + cal.get(MONTH);
  }

  private String monthKey(MonthDescriptor month) {
    return month.getYear() + "-" + month.getMonth();
  }
  //清楚旧的选择
  private void clearOldSelections() {
    for (MonthCellDescriptor selectedCell : selectedCells) {
      // De-select the currently-selected cell.
      selectedCell.setSelected(false);

      if (dateListener != null) {
        Date selectedDate = selectedCell.getDate();
         dateListener.onDateUnselected(selectedDate);
      }
    }
    selectedCells.clear();
    selectedCals.clear();
  }

  private Date applyMultiSelect(Date date, Calendar selectedCal) {
    /*  选择日期去重*/
      for (MonthCellDescriptor selectedCell : selectedCells) {
        if (selectedCell.getDate().equals(date)) {
          // De-select the currently-selected cell.
          selectedCell.setSelected(false);
          selectedCells.remove(selectedCell);
          date = null;
          break;
        }
      }

      for (Calendar cal : selectedCals) {
        if (sameDate(cal, selectedCal)) {
          selectedCals.remove(cal);
          break;
        }
      }

    return date;
  }




  /** Hold a cell with a month-index. */
  private static class MonthCellWithMonthIndex {
    public MonthCellDescriptor cell;
    public int monthIndex;

    public MonthCellWithMonthIndex(MonthCellDescriptor cell, int monthIndex) {
      this.cell = cell;
      this.monthIndex = monthIndex;
    }
  }

  /** Return cell and month-index (for scrolling) for a given Date. */
  private MonthCellWithMonthIndex getMonthCellWithIndexByDate(Date date) {
    Calendar searchCal = Calendar.getInstance(timeZone, locale);
    searchCal.setTime(date);
    String monthKey = monthKey(searchCal);
    Calendar actCal = Calendar.getInstance(timeZone, locale);

    int index = cells.getIndexOfKey(monthKey);
    List<List<MonthCellDescriptor>> monthCells = cells.get(monthKey);
    for (List<MonthCellDescriptor> weekCells : monthCells) {
      for (MonthCellDescriptor actCell : weekCells) {
        actCal.setTime(actCell.getDate());
        if (sameDate(actCal, searchCal) && actCell.isSelectable()) {
          return new MonthCellWithMonthIndex(actCell, index);
        }
      }
    }
    return null;
  }

  private class MonthAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private SparseArray<MonthView> mSparseIntArray=new SparseArray<MonthView>();
    public MonthView getPositionView(int position){
      return mSparseIntArray.get(position);
    }
    private MonthAdapter() {
      inflater = LayoutInflater.from(getContext());
    }

    @Override public boolean isEnabled(int position) {
      // Disable selectability: each cell will handle that itself.
      return false;
    }

    @Override public int getCount() {
      return months.size();
    }

    @Override public Object getItem(int position) {
      return months.get(position);
    }

    @Override public long getItemId(int position) {
      return position;
    }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
      MonthView monthView = (MonthView) convertView;
      if (monthView == null //
              || !monthView.getTag(R.id.day_view_adapter_class).equals(dayViewAdapter.getClass())) {
        monthView =
                MonthView.create(parent, inflater, weekdayNameFormat, listener, today, dividerColor,
                        dayBackgroundResId, dayTextColorResId, titleTextColor, displayHeader,
                        headerTextColor, decorators, locale, dayViewAdapter);
        monthView.setTag(R.id.day_view_adapter_class, dayViewAdapter.getClass());
      } else {
        monthView.setDecorators(decorators);
      }
      monthView.init(months.get(position), cells.getValueAtIndex(position), displayOnly,
              titleTypeface, dateTypeface);
      mSparseIntArray.append(position,monthView);
      return monthView;
    }
  }

  List<List<MonthCellDescriptor>> getMonthCells(MonthDescriptor month, Calendar startCal) {
    Calendar cal = Calendar.getInstance(timeZone, locale);
    cal.setTime(startCal.getTime());
    List<List<MonthCellDescriptor>> cells = new ArrayList<>();
    cal.set(DAY_OF_MONTH, 1);
    int firstDayOfWeek = cal.get(DAY_OF_WEEK);
    int offset = cal.getFirstDayOfWeek() - firstDayOfWeek;
    if (offset > 0) {
      offset -= 7;
    }
    cal.add(DATE, offset);

    Calendar minSelectedCal = minDate(selectedCals);
    Calendar maxSelectedCal = maxDate(selectedCals);

    while ((cal.get(MONTH) < month.getMonth() + 1 || cal.get(YEAR) < month.getYear()) //
            && cal.get(YEAR) <= month.getYear()) {
      Logr.d("Building week row starting at %s", cal.getTime());
      List<MonthCellDescriptor> weekCells = new ArrayList<>();
      cells.add(weekCells);
      for (int c = 0; c < 7; c++) {
        Date date = cal.getTime();
        boolean isCurrentMonth = cal.get(MONTH) == month.getMonth();
        boolean isSelected = isCurrentMonth && containsDate(selectedCals, cal);
        boolean isSelectable =
                isCurrentMonth && betweenDates(cal, minCal, maxCal) && isDateSelectable(date);
        boolean isToday = sameDate(cal, today);
        int value = cal.get(DAY_OF_MONTH);
        //走这里的时候，我的初始设置的日期还没有走到这里啊
        weekCells.add(
                new MonthCellDescriptor(date, isCurrentMonth, isSelectable, isSelected, isToday,false
                        ,false,false,value, null));
        cal.add(DATE, 1);
      }
    }
    return cells;
  }

  private boolean containsDate(List<Calendar> selectedCals, Date date) {
    Calendar cal = Calendar.getInstance(timeZone, locale);
    cal.setTime(date);
    return containsDate(selectedCals, cal);
  }

  private static boolean containsDate(List<Calendar> selectedCals, Calendar cal) {
    for (Calendar selectedCal : selectedCals) {
      if (sameDate(cal, selectedCal)) {
        return true;
      }
    }
    return false;
  }

  private static Calendar minDate(List<Calendar> selectedCals) {
    if (selectedCals == null || selectedCals.size() == 0) {
      return null;
    }
    Collections.sort(selectedCals);
    return selectedCals.get(0);
  }

  private static Calendar maxDate(List<Calendar> selectedCals) {
    if (selectedCals == null || selectedCals.size() == 0) {
      return null;
    }
    Collections.sort(selectedCals);
    return selectedCals.get(selectedCals.size() - 1);
  }

  private static boolean sameDate(Calendar cal, Calendar selectedDate) {
    return cal.get(MONTH) == selectedDate.get(MONTH)
            && cal.get(YEAR) == selectedDate.get(YEAR)
            && cal.get(DAY_OF_MONTH) == selectedDate.get(DAY_OF_MONTH);
  }

  private static boolean betweenDates(Calendar cal, Calendar minCal, Calendar maxCal) {
    final Date date = cal.getTime();
    return betweenDates(date, minCal, maxCal);
  }

  static boolean betweenDates(Date date, Calendar minCal, Calendar maxCal) {
    final Date min = minCal.getTime();
    return (date.equals(min) || date.after(min)) // >= minCal
            && date.before(maxCal.getTime()); // && < maxCal
  }

  private static boolean sameMonth(Calendar cal, MonthDescriptor month) {
    return (cal.get(MONTH) == month.getMonth() && cal.get(YEAR) == month.getYear());
  }

  private boolean isDateSelectable(Date date) {
    return dateConfiguredListener == null || dateConfiguredListener.isDateSelectable(date);
  }

  public void setOnDateSelectedListener(OnDateSelectedListener listener) {
    dateListener = listener;
  }

  /**
   * Set a listener to react to user selection of a disabled date.
   *
   * @param listener the listener to set, or null for no reaction
   */
  public void setOnInvalidDateSelectedListener(OnInvalidDateSelectedListener listener) {
    invalidDateListener = listener;
  }

  /**
   * Set a listener used to discriminate between selectable and unselectable dates. Set this to
   * disable arbitrary dates as they are rendered.
   * <p>
   * Important: set this before you call {@link #init(Date, Date)} methods.  If called afterwards,
   * it will not be consistently applied.
   */
  public void setDateSelectableFilter(DateSelectableFilter listener) {
    dateConfiguredListener = listener;
  }

  /**
   * Set an adapter used to initialize {@link CalendarCellView} with custom layout.
   * <p>
   * Important: set this before you call {@link #init(Date, Date)} methods.  If called afterwards,
   * it will not be consistently applied.
   */
  public void setCustomDayView(DayViewAdapter dayViewAdapter) {
    this.dayViewAdapter = dayViewAdapter;
    if (null != adapter) {
      adapter.notifyDataSetChanged();
    }
  }

  /** Set a listener to intercept clicks on calendar cells. */
  public void setCellClickInterceptor(CellClickInterceptor listener) {
    cellClickInterceptor = listener;
  }

  /**
   * Interface to be notified when a new date is selected or unselected. This will only be called
   * when the user initiates the date selection.  If you call {@link #selectDate(Date)} this
   * listener will not be notified.
   *
   * @see #setOnDateSelectedListener(OnDateSelectedListener)
   */
  public interface OnDateSelectedListener {
    void onDateSelected(Date date);

    void onDateUnselected(Date date);
  }

  /**
   * Interface to be notified when an invalid date is selected by the user. This will only be
   * called when the user initiates the date selection. If you call {@link #selectDate(Date)} this
   * listener will not be notified.
   *
   * @see #setOnInvalidDateSelectedListener(OnInvalidDateSelectedListener)
   */
  public interface OnInvalidDateSelectedListener {
    void onInvalidDateSelected(Date date);
  }

  /**
   * Interface used for determining the selectability of a date cell when it is configured for
   * display on the calendar.
   *
   * @see #setDateSelectableFilter(DateSelectableFilter)
   */
  public interface DateSelectableFilter {
    boolean isDateSelectable(Date date);
  }

  /**
   * Interface to be notified when a cell is clicked and possibly intercept the click.  Return true
   * to intercept the click and prevent any selections from changing.
   *
   * @see #setCellClickInterceptor(CellClickInterceptor)
   */
  public interface CellClickInterceptor {
    boolean onCellClicked(Date date);
  }

  private class DefaultOnInvalidDateSelectedListener implements OnInvalidDateSelectedListener {
    @Override public void onInvalidDateSelected(Date date) {
      String errMessage =
              getResources().getString(R.string.invalid_date, fullDateFormat.format(minCal.getTime()),
                      fullDateFormat.format(maxCal.getTime()));
      Toast.makeText(getContext(), errMessage, Toast.LENGTH_SHORT).show();
    }
  }

  //单选日期和多选日期监听
  public interface OnSingleAndMutipleChoiceListener {
    void onSingleChoice(Date date);
    void onMutipleChoice(Date startDate,Date endDate);
  }
  public OnSingleAndMutipleChoiceListener mOnSingleAndMutipleChoiceListener;
  public void setOnSingleAndMutipleChoiceListener(OnSingleAndMutipleChoiceListener onSingleAndMutipleChoiceListener){
    mOnSingleAndMutipleChoiceListener=onSingleAndMutipleChoiceListener;
  }
}