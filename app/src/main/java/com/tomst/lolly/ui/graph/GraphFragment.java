package com.tomst.lolly.ui.graph;


import static android.graphics.Color.BLACK;

import android.content.Context;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.DashPathEffect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalField;
import java.util.ArrayList;

import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.LegendEntry;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.CombinedData;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.formatter.DefaultAxisValueFormatter;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.data.LineDataSet;

import com.github.mikephil.charting.utils.ColorTemplate;
import com.tomst.lolly.core.CSVReader;
import com.tomst.lolly.core.CSVFile;
import com.tomst.lolly.core.Constants;
import com.tomst.lolly.core.OnProListener;
import com.tomst.lolly.core.TDendroInfo;
import com.tomst.lolly.core.TMereni;
import com.tomst.lolly.core.TPhysValue;
import com.tomst.lolly.databinding.FragmentGraphBinding;
import com.tomst.lolly.core.DmdViewModel;


import org.apache.commons.lang3.ObjectUtils;

import java.io.File;
import java.util.TimeZone;


@RequiresApi(api = Build.VERSION_CODES.O)
public class GraphFragment extends Fragment
{
    // constants for loading CSV files
    private static final String DATE_PATTERN = "yyyy.MM.dd HH:mm";
    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_PATTERN);
    private static final String SHORT_DATE_PATTERN = "M.d.yy";
    private DateTimeFormatter display_formatter = DateTimeFormatter.ofPattern(SHORT_DATE_PATTERN);
    private static final byte SERIAL_INDEX = 0;
    private static final byte LONGITUDE_INDEX = 1;
    private static final byte LATITUDE_INDEX = 2;
    private static final byte PICTURE_INDEX = 4;


    // constants for merging CSV files
    private static final int HEADER_LINE_LENGTH = 3;
    private static final int SERIAL_NUMBER_INDEX = 1;
    private static final String DEFAULT_SERIAL_NUMBER_VALUE = "Unknown";

    // constants for loading measurements
    private static final byte DATETIME_INDEX = 1;
    private static final byte TEMP1_INDEX = 3;
    private static final byte TEMP2_INDEX = 4;
    private static final byte TEMP3_INDEX = 5;
    private static final byte HUMIDITY_INDEX = 6;
    private static final byte MVS_INDEX = 7;
    private final String TMD_DELIM = " ";
    private final String TAG = "GraphFragment";


    // CSV loading
    public int headerIndex = 0;
    public int numDataSets = 0;
    public float[] intervals = {10f, 10f};

    public class LongAxisValueFormatter extends ValueFormatter {
        @Override
        public String getAxisLabel(float value, AxisBase axis) {

            // Convert float value (epoch seconds) to LocalDateTime
            LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond((long) value),
                   ZoneOffset.MAX);

            // Format LocalDateTime to string
            return dateTime.format(display_formatter);
        }
    }

    // visualization data holders
    //private final int barCount = 12;
    private ArrayList<ILineDataSet> dataSets = new ArrayList<>();
    private ArrayList<TDendroInfo> dendroInfos = new ArrayList<>();
    private ArrayList<LegendEntry> LegendEntrys = new ArrayList<>();


    // graphing
    private CombinedChart chart;
    private CombinedData combinedData;

    private SeekBar seekBarX;
    private TextView tvX;


    private final int[] colors = new int[] {
            ColorTemplate.VORDIPLOM_COLORS[0],
            ColorTemplate.VORDIPLOM_COLORS[1],
            ColorTemplate.VORDIPLOM_COLORS[2]
    };

    private FragmentGraphBinding binding;

    private  DmdViewModel dmd;

    private Integer fIdx = 0;


    protected Handler handler = new Handler(Looper.getMainLooper())
    {
        @Override
        public void handleMessage(Message msg)
        {
            TMereni mer = (TMereni) msg.obj;
            //Log.d(TAG,String.valueOf(mer.idx));
            dmd.AddMereni(mer);
            fIdx ++;
        }
    };


    @Override
    public void onDestroy()
    {
        super.onDestroy();
        //dmd.sendMessageToGraph("");
    }


    @Override
    public void onStop()
    {
        dmd.sendMessageToGraph("");
        dmd.ClearMereni();

        super.onStop();
    }

    private void DisplayData()
    {
        Log.d(TAG, "DisplayData");
        boolean firstOfItsKind = true;
        int ogHeaderIndex = headerIndex;
        LineDataSet d = null;
        LineData lines;

        headerIndex = 0;
        do
        {
            Log.d(TAG, "loading dataset: " + headerIndex);
            // line graph
            d = SetLine(dendroInfos.get(headerIndex).vT1, TPhysValue.vT1);
            dataSets.add(d);
            d = SetLine(dendroInfos.get(headerIndex).vT2, TPhysValue.vT2);
            dataSets.add(d);
            d = SetLine(dendroInfos.get(headerIndex).vT3, TPhysValue.vT3);
            dataSets.add(d);
            // growth
            d = SetLine(dendroInfos.get(headerIndex).vHA, TPhysValue.vHum);
            dataSets.add(d);
            lines = new LineData(dataSets);
            combinedData.setData(lines);

            // combinedData.setData(generateBarData());
            chart.setData(combinedData);
            chart.getAxisLeft().setEnabled(true);
            chart.getAxisRight().setEnabled(true);

            // startup animation
            chart.animateX(2000, Easing.EaseInCubic);

            LegendEntry legendEntry = new LegendEntry();
            legendEntry.label = dendroInfos.get(headerIndex).serial;
            legendEntry.formColor = dendroInfos.get(headerIndex).color;
            LegendEntrys.add(legendEntry);

            headerIndex++;
        }
        while (headerIndex < numDataSets);

        Legend l = chart.getLegend();
        l.setCustom(LegendEntrys);
        // sets view to start of graph and zooms into x axis by 7x
        chart.zoomAndCenterAnimated(
                7f, 1f,
                0, 0,
                chart.getAxisLeft().getAxisDependency(), 3000
        );

        //Default: Do Not Display T2 and T3
        DoBtnClick(binding.vT2);
        DoBtnClick(binding.vT3);

        headerIndex = ogHeaderIndex;
    }

    private void setupDMDGraph(String serialNumber) {
        numDataSets = 1;

        TDendroInfo defaultDendroInfo = new TDendroInfo(
                serialNumber, null, null
        );
        dendroInfos.add(0, defaultDendroInfo);

        dendroInfos.get(0).vT1 = dmd.getT1();
        dendroInfos.get(0).vT2 = dmd.getT2();
        dendroInfos.get(0).vT3 = dmd.getT3();
        dendroInfos.get(0).vHA = dmd.getHA();
    }

    private boolean loadCSVFile(String fileName)
    {
        Toast.makeText(getContext(), "loading", Toast.LENGTH_SHORT).show();
        float dateNum;
        //boolean firstDate = true;
        boolean hasHeader = true;
        String currentLine = "";
        int lines=0;

        if (fileName.length()<1)
            return false;

        CSVFile csv = CSVFile.open(fileName, CSVFile.READ_MODE);

        currentLine = csv.readLine();
        // length 1 if dataset count is first line
        if (currentLine.split(";").length == 1) {
            // file has a header
            // count data sets
            numDataSets = Integer.parseInt(currentLine.split(";")[0]);

            // read file header
            while (headerIndex < numDataSets) {
                currentLine = csv.readLine();
                String[] lineOfFile = currentLine.split(";");

                String serial = lineOfFile[SERIAL_INDEX];
                Long longitude = Long.parseLong(lineOfFile[LONGITUDE_INDEX]);
                Long latitude = Long.parseLong(lineOfFile[LATITUDE_INDEX]);
                TDendroInfo dendroInfo = new TDendroInfo(
                        serial, longitude, latitude
                );
                dendroInfos.add(headerIndex, dendroInfo);

                headerIndex++;
            }

            // get first line of datasets
            currentLine = csv.readLine();
        }
        else { // file does not have a header
            hasHeader = false;

            numDataSets = 1;

            // serial number is unknown. Get from filename if possible
            String serialNumber = getSerialNumberFromFileName(fileName);

            TDendroInfo defaultDendroInfo = new TDendroInfo(
                    serialNumber, null, null
            );
            dendroInfos.add(headerIndex, defaultDendroInfo);
        }

        // read data
        headerIndex = -1;
        while (currentLine != "")
        {
            TMereni mer = processLine(currentLine);
            String[] lineOfFile = currentLine.split(";");

            if (!hasHeader) {
                headerIndex = 0;
            }

            if (hasHeader && mer.Serial != null)
            {
                headerIndex++;

                if (headerIndex < numDataSets)
                {
                    dendroInfos.get(headerIndex).serial = mer.Serial;
                }
            }
            else
            {
                //number of minutes from the first date plotted
                //dateNum = (mer.dtm.toEpochSecond(ZoneOffset.MAX) - originDate) / 60;
                dateNum = mer.dtm.toEpochSecond(ZoneOffset.MAX);

                dendroInfos.get(headerIndex).mers.add(mer);
                dendroInfos.get(headerIndex).vT1.add(
                        new Entry(dateNum, (float) mer.t1)
                );
                dendroInfos.get(headerIndex).vT2.add(
                        new Entry(dateNum, (float) mer.t2)
                );
                dendroInfos.get(headerIndex).vT3.add(
                        new Entry(dateNum, (float) mer.t3)
                );
                dendroInfos.get(headerIndex).vHA.add(
                        new Entry(dateNum, (float) mer.hum)
                );
            }

            // move to next line
            currentLine = csv.readLine();
            lines++;
        }
        return (lines>0);
    }

    private TMereni processLine(String line)
    {
        long currTime;
        String[] lineOfFile = line.split(";");
        LocalDateTime dateTime = null;
        LocalDateTime currDate;

        TMereni mer = new TMereni();
        if (lineOfFile.length == 1)
        {
            mer.Serial = lineOfFile[SERIAL_INDEX];
        }
        else
        {
            //must fix date parser
            try
            {
                dateTime = LocalDateTime.parse(lineOfFile[DATETIME_INDEX], formatter);
                mer.dtm = dateTime;
                mer.day = dateTime.getDayOfMonth();
            }
            catch (Exception e)
            {
                System.out.println(e);
            }

            // replaces all occurrences of 'a' to 'e'
            String T1 = lineOfFile[TEMP1_INDEX]
                    .replace(',', '.');
            // replaces all occurrences of 'a' to 'e'
            String T2 = lineOfFile[TEMP2_INDEX]
                    .replace(',', '.');
            // replaces all occurrences of 'a' to 'e'
            String T3 = lineOfFile[TEMP3_INDEX]
                    .replace(',', '.');

            mer.Serial = null;
            mer.t1 = Float.parseFloat(T1);
            mer.t2 = Float.parseFloat(T2);
            mer.t3 = Float.parseFloat(T3);
            mer.hum = Integer.parseInt(lineOfFile[HUMIDITY_INDEX]);
            mer.mvs = Integer.parseInt(lineOfFile[MVS_INDEX]);
        }

        return mer;
    }


    private void DoBtnClick(View view)
    {
        boolean checked = ((CheckBox) view).isChecked();
        Object ob = ((CheckBox) view).getTag();
        int tag = Integer.valueOf(ob.toString());
        if (tag <= 0)
        {
            throw new UnsupportedOperationException(
                    "Selected line dataset doesn't exists"
            );
        }

        do {
            dataSets.get(tag - 1).setVisible(checked);
            chart.invalidate();
            tag+=4;
        } while ( tag <= dataSets.size());

    }


    // nahraje data pridane TMD adapterem ve fragmentu HomeFragment
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
    }

    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        Log.d("MERGECALL", "MERGE IS CALLED");
        GraphViewModel readerViewModel =
                new ViewModelProvider(this).get(GraphViewModel.class);
        binding = FragmentGraphBinding.inflate(
                inflater, container, false
        );
        View root = binding.getRoot();

        dmd = new ViewModelProvider(getActivity()).get(DmdViewModel.class);

        // sem me dostane observer nastaveny v prohlizecce souboru
        // observer message for TMD adapter
        dmd.getMessageContainerGraph()
                .observe(getViewLifecycleOwner(), msg ->
                {
                    int loadCSVCode = 0;
                    Log.d("GRAPH", "Received: " + msg);
                    if (msg.split(TMD_DELIM)[0].equals("TMD"))
                    {
                        /*
                        // vytahne data z dmd, ktere sem poslal TMD adapter
                        // pulls data from dendrometer
                        LoadDmdData();

                        //Default: Do Not Display T2 and T3
                        DoBtnClick(binding.vT2);
                        DoBtnClick(binding.vT3);
                        */

                        setupDMDGraph(msg.split(TMD_DELIM)[1]);
                        DisplayData();
                    }
                    else
                    {
                        String[] fileNames = msg.split(";");

                        boolean hasData=false;
                        if (fileNames.length > 1)
                        {
                            String mergedFileName = mergeCSVFiles(fileNames);
                            Log.d(
                                    "GRAPH",
                                    "Merged file name = "
                                            + mergedFileName
                            );

                            hasData =  loadCSVFile(mergedFileName);
                        }
                        else
                        {
                            hasData =  loadCSVFile(fileNames[0]);
                        }

                        if (hasData)
                          DisplayData();
                    }

                    dmd.getMessageContainerGraph()
                            .removeObservers(getViewLifecycleOwner());
                });

        CheckBox cbT1 = binding.vT1;
        cbT1.setOnClickListener(view ->
        {
            DoBtnClick(view);
        });
        cbT1.setChecked(true);
        CheckBox cbT2 = binding.vT2;
        cbT2.setOnClickListener(view ->
        {
            DoBtnClick(view);
        });
        cbT2.setChecked(false);
        CheckBox cbT3 = binding.vT3;
        cbT3.setOnClickListener(view ->
        {
            DoBtnClick(view);
        });
        cbT3.setChecked(false);
        CheckBox cbHum = binding.vGrowth;
        cbHum.setOnClickListener(view ->
        {
            DoBtnClick(view);
        });
        cbHum.setChecked(true);

        getActivity().setTitle("Lolly 4");
        chart = binding.chart1;
        // chart.getDescription().setText(CsvFileName);
        chart.setTouchEnabled(true);
        chart.setDragDecelerationFrictionCoef(0.9f);

        // enable scaling and dragging
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setDrawGridBackground(false);
        chart.setHighlightPerDragEnabled(true);

        Description description = new Description();
        description.setText("");
        chart.setDescription(description);

        // set an alternative background color
        // chart.setBackgroundColor(Color.WHITE);
        chart.setViewPortOffsets(0f, 0f, 0f, 0f);
        // if disabled, scaling can be done on x- and y-axis separately
        chart.setPinchZoom(false);

        // osa humidit
        YAxis rightAxis = chart.getAxisRight();
        rightAxis.setDrawGridLines(false);
        rightAxis.setPosition(YAxis.YAxisLabelPosition.INSIDE_CHART);
        //rightAxis.setAxisMinimum(0f); // this replaces setStartAtZero(true)
        //rightAxis.setAxisMaximum(1000f);

        // osa teplot
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLines(false);
        leftAxis.setPosition(YAxis.YAxisLabelPosition.INSIDE_CHART);
        //leftAxis.setAxisMinimum(-10f); // this replaces setStartAtZero(true)
        //leftAxis.setAxisMaximum(30f);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.TOP_INSIDE);
        xAxis.setTextSize(15f);
        xAxis.setDrawAxisLine(true);
        xAxis.setDrawGridLines(true);
        xAxis.setTextColor(BLACK);
        xAxis.setTextSize(10f);
        xAxis.setCenterAxisLabels(true);
        xAxis.setGranularity(1f); // one hour
        xAxis.setValueFormatter(new LongAxisValueFormatter());
        xAxis.setLabelRotationAngle(60f);

        combinedData = new CombinedData();

        return root;
    }

    private String getSerialNumberFromFileName(String fileName) {
        // filename should look like "data_92221411_2023_09_26_0.csv"
        // serial number should be the second value. No other way to get the serial number
        // if not in the title, just use a default value
        String[] titleSplit = fileName.split("_");
        String serialNumber;
        if (titleSplit.length > SERIAL_NUMBER_INDEX) {
            serialNumber = fileName.split("_")[SERIAL_NUMBER_INDEX];
        }
        else {
            // could theoretically add a dataset count to the end of this unknown
            // but then it becomes an issue when merging several unknown datasets together
            // would end up looking like: unknown1, unknown2, unknown1
            serialNumber = DEFAULT_SERIAL_NUMBER_VALUE;
        }

        return serialNumber;
    }

    private String mergeCSVFiles(String[] fileNames)
    {
        Log.d("MERGECALL", "Merge is called");
        String[] strArr;
        LocalDateTime dateTime;
        long currTime;

        final String LAST_OCCURENCE = ".*/";
        // final String parent_dir = file_names[0].split(LAST_OCCURENCE)[0];
        // for testing purposes only
        final String parentDir = "/storage/emulated/0/Documents/";
        String tempFileName = parentDir + "temp.csv";
        String mergedFileName = parentDir + fileNames[0]
                .split(LAST_OCCURENCE)[1]
                .replace(".csv", "");
        for (int i = 1; i < fileNames.length; i += 1)
        {
            mergedFileName += "-" + fileNames[i]
                    .split(LAST_OCCURENCE)[1]
                    .replace(".csv", "");
        }
        mergedFileName += ".csv";

        if (CSVFile.exists(mergedFileName))
        {
            CSVFile.delete(mergedFileName);
        }

        int dataSetCnt = 0;
        String header = "";
        CSVFile tempFile = CSVFile.create(tempFileName);
        for (String fileName : fileNames)
        {
            Log.d("MERGECALL", "Enters merge loop");
            CSVFile csvFile = CSVFile.open(fileName, CSVFile.READ_MODE);

            // read in first line of file
            String currentLine = csvFile.readLine();

            // if first line is dataset count (only one value)
            if (currentLine.split(";").length == 1) {
                // file has header
                // count the data sets
                dataSetCnt += Integer.parseInt(currentLine.split(";")[0]);
                // read serial number(s) is always first line in data set
                while ((currentLine = csvFile.readLine())
                        .split(";").length == HEADER_LINE_LENGTH
                ) {
                    header += currentLine + "\n";
                }
                // write serial number
                tempFile.write(currentLine + "\n");
            }
            else { // otherwise the file being merged does not have a header
                // file does not have a header
                dataSetCnt += 1;

                // serial number is unknown. Get from filename if possible
                String serialNumber = getSerialNumberFromFileName(fileName);

                String headerLine = serialNumber + ";0;0;\n";
                header += headerLine;

                // write the serial number
                tempFile.write(serialNumber + "\n");

                // write the first line of the dataset
                tempFile.write(currentLine + "\n");
            }

            while((currentLine = csvFile.readLine()).contains(";"))
            {
                tempFile.write(currentLine + "\n");
            }
            csvFile.close();
        }
        tempFile.close();

        header = dataSetCnt + ";\n" + header;

        CSVFile mergedFile = CSVFile.create(mergedFileName);
        mergedFile.write(header);
        tempFile = CSVFile.open(tempFileName, CSVFile.READ_MODE);
        String line = "";
        while ((line = tempFile.readLine()) != "")
        {
            mergedFile.write(line + "\n");
        }
        mergedFile.close();
        tempFile.close();
        CSVFile.delete(parentDir + "temp.csv");

        return mergedFileName;
    }

    private LineDataSet SetLine(ArrayList<Entry> vT, TPhysValue val)
    {
        Log.d(TAG, "SetLine");
        int lineColor=0;
        int colorStep=255/3;

        LineDataSet set =
                new LineDataSet(vT, "DataSet " + (val.ordinal() + 1));
        set.setDrawValues(false);
        set.setDrawCircles(false);
        set.setMode(LineDataSet.Mode.LINEAR);
        set.setDrawFilled(false);
        set.setLabel(val.valToString(val));
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setLineWidth(1f);

        //differentiating datasets by color (supports max 9 datasets)
        if (numDataSets > 4)
        {
            if (headerIndex < 3) {
                //set.setColor(Color.rgb(headerIndex * colorStep, 0, 127));
                lineColor = Color.rgb(headerIndex * colorStep, 0, 127);
            } else if (headerIndex < 6) {
                //set.setColor(Color.rgb(127, (headerIndex - 3) * colorStep, 0));
                lineColor = Color.rgb(127, (headerIndex - 3) * colorStep, 0);
            } else {
                lineColor = Color.rgb(0, 127, (headerIndex - 6) * colorStep);
            }
        }
        else     //maximum color differential for 1 to 4 datasets
        {
            if (headerIndex == 0)
            {
                //GREEN
                lineColor = Color.rgb(20,83,45);
            }
            else if (headerIndex == 1)
            {
                //ORANGE
                lineColor = Color.rgb(241,168,51);
            }
            else if (headerIndex == 2)
            {
                //DARK BROWN
                lineColor = Color.rgb(52,21,0);
            }
            else if (headerIndex == 3)
            {
                //LIGHT BLUE
                lineColor = Color.rgb(0,197,255);
            }
        }

        //differentiating values by different dash patterns
        switch (val)
        {
            case vT1:
                set.setLineWidth(5f);
                break;

            case vT2:
                set.setLineWidth(2f);
                break;

            case vT3:
                set.setLineWidth(1f);
                break;

            case vHum:
                set.setLineWidth(5f);
                set.enableDashedLine(20f, 20f, 0f);
                if (!dendroInfos.isEmpty())
                {
                    dendroInfos.get(headerIndex).color = lineColor;
                }

            case vAD:
                set.setLineWidth(5f);

            case vMicro:
                set.setAxisDependency(YAxis.AxisDependency.RIGHT);
                break;

            default:
                throw new UnsupportedOperationException("Not yet implemented");
        }
        set.setColor(lineColor);

        return set;
    }


    protected float getRandom(float range, float start)
    {
        return (float) (Math.random() * range) + start;
    }

/*
    private BarData generateBarData()
    {
        ArrayList<BarEntry> entries1 = new ArrayList<>();
        ArrayList<BarEntry> entries2 = new ArrayList<>();

        for (int index = 0; index < barCount; index++)
        {
            entries1.add(new BarEntry(0, getRandom(25, 25)));

            // stacked
            entries2.add(new BarEntry(
                    0,
                    new float[] {
                            getRandom(13, 12),
                            getRandom(13, 12)
                    }));
        }

        BarDataSet set1 = new BarDataSet(entries1, "Bar 1");
        set1.setColor(Color.rgb(60, 220, 78));
        set1.setValueTextColor(Color.rgb(60, 220, 78));
        set1.setValueTextSize(10f);
        set1.setAxisDependency(YAxis.AxisDependency.LEFT);

        BarDataSet set2 = new BarDataSet(entries2, "");
        set2.setStackLabels(new String[]{"Stack 1", "Stack 2"});
        set2.setColors(
                Color.rgb(61, 165, 255),
                Color.rgb(23, 197, 255)
        );
        set2.setValueTextColor(Color.rgb(61, 165, 255));
        set2.setValueTextSize(10f);
        set2.setAxisDependency(YAxis.AxisDependency.LEFT);

        float groupSpace = 0.06f;
        float barSpace = 0.02f; // x2 dataset
        float barWidth = 0.45f; // x2 dataset
        // (0.45 + 0.02) * 2 + 0.06 = 1.00 -> interval per "group"

        BarData d = new BarData(set1, set2);
        d.setBarWidth(barWidth);

        // make this BarData object grouped
        d.groupBars(0, groupSpace, barSpace); // start at x = 0

        return d;
    }
*/

    /*
    TODO:
        1) Rename DmdViewModel to be something more akin to the function of a
         ViewModel
        2) Shrink definition
     */

//    private void LoadDmdData()
//    {
//        LineDataSet d = null;
//
//        // **** linearni graf
//        d = SetLine(dmd.getT1(),TPhysValue.vT1);
//        dataSets.add(d);
//        d = SetLine(dmd.getT2(),TPhysValue.vT2);
//        dataSets.add(d);
//        d = SetLine(dmd.getT3(),TPhysValue.vT3);
//        dataSets.add(d);
//        // humidity
//        d = SetLine(dmd.getHA(),TPhysValue.vHum);
//        dataSets.add(d);
//        LineData lines = new LineData(dataSets);
//        combinedData.setData(lines);
//        // combinedData.setData(generateBarData());
//        chart.setData(combinedData);
//        chart.getAxisLeft().setEnabled(true);
//        chart.getAxisRight().setEnabled(true);
//
//        chart.invalidate();
//    }


    @Override
    public void onDestroyView()
    {
        super.onDestroyView();
        binding = null;
    }

}