package math.android.cryptobot;

import android.animation.ValueAnimator;
import android.app.ProgressDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutionException;


public class MainActivity extends FragmentActivity implements WalletFragment.OnWalletCompleteListener, RatesFragment.OnRatesCompleteListener, SettingsFragment.OnSettingsCompleteListener, HttpGetRequest.OnRequestCompleted {

    MyAdapter mAdapter;
    ViewPager mPager;

    // wallet
    public int walletUSDvalue0 = 100000;
    public int walletUSDvalue = walletUSDvalue0;
    public int walletBTCvalue = 10;

    TextView usdBalanceValue;
    EditText usdToAdd;
    Button usdAddButton;

    TextView btcBalanceValue;
    EditText btcToAdd;
    Button btcAddButton;

    // rates
    GraphView graph;
    public int dataCount = 0;
    public int[] currentBidValues = new int[]{0,0,0,0,0,0,0,0,0,0};
    public int currentBidValuesLength = 0;
    public LineGraphSeries<DataPoint> bidSeries;
    public int[] currentAskValues = new int[]{0,0,0,0,0,0,0,0,0,0};
    public int currentAskValuesLength = 0;
    public LineGraphSeries<DataPoint> askSeries;
    RadioGroup radioWhich;
    RadioButton selectedWhich;

    public String exchange1_url = "https://api.kraken.com/0/public/Ticker?pair=XXBTZUSD";
    public String exchange2_url = "https://api.bitfinex.com/v1/pubticker/btcusd";

    public int exchange1Ask;
    public int exchange1Bid;
    public int exchange2Ask;
    public int exchange2Bid;

    TextView exchange1AskValue;
    TextView exchange1BidValue;
    TextView exchange2AskValue;
    TextView exchange2BidValue;
    Button requestButton;
    ProgressBar progressLoader;

    public int requestsCompleted = 0;

    // settings
    CheckBox checkInfinite;
    Spinner differenceChooser;
    String[] differences = new String[]{"0","5","10","20","50","100","200"};
    public int chosenDifference = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAdapter = new MyAdapter(getSupportFragmentManager());

        mPager = (ViewPager)findViewById(R.id.view_pager);
        mPager.setAdapter(mAdapter);

        BottomNavigationView bottomNavigationView = (BottomNavigationView)
                findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnNavigationItemSelectedListener(
                new BottomNavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                        switch (item.getItemId()) {
                            case R.id.navigation_wallet:
                                mPager.setCurrentItem(0);
                                break;
                            case R.id.navigation_rates:
                                mPager.setCurrentItem(1);
                                //drawGraph();
                                break;
                            case R.id.navigation_settings:
                                mPager.setCurrentItem(2);
                                break;
                        }
                        return false;
                    }
                });
    }

    public void onWalletComplete() {
        usdBalanceValue = (TextView)findViewById(R.id.usd_balance_value);
        usdBalanceValue.setText(Integer.toString(walletUSDvalue));
        usdToAdd = (EditText)findViewById(R.id.usd_to_add);
        usdAddButton = (Button)findViewById(R.id.usd_add_button);
        usdAddButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                walletUSDvalue0 = walletUSDvalue;
                walletUSDvalue += Integer.parseInt(usdToAdd.getText().toString());
                startCountAnimation();
            }
        });

        btcBalanceValue = (TextView)findViewById(R.id.btc_balance_value);
        btcBalanceValue.setText(Integer.toString(walletBTCvalue));
        btcToAdd = (EditText)findViewById(R.id.btc_to_add);
        btcAddButton = (Button)findViewById(R.id.btc_add_button);
        btcAddButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                walletBTCvalue += Integer.parseInt(btcToAdd.getText().toString());
                btcBalanceValue.setText(Integer.toString(walletBTCvalue));
            }
        });
    }

    public void onRatesComplete() {
        graph = (GraphView) findViewById(R.id.graph);
        bidSeries = new LineGraphSeries<>(generateData("bid"));
        bidSeries.setColor(getColor(R.color.green));
        askSeries = new LineGraphSeries<>(generateData("ask"));
        askSeries.setColor(getColor(R.color.red));
        GridLabelRenderer glr = graph.getGridLabelRenderer();
        glr.setPadding(80);
        graph.addSeries(bidSeries);
        graph.addSeries(askSeries);

        radioWhich = (RadioGroup) findViewById(R.id.radio_which);

        exchange1AskValue = (TextView)findViewById(R.id.exchange1_ask_value);
        exchange1BidValue = (TextView)findViewById(R.id.exchange1_bid_value);
        exchange2AskValue = (TextView)findViewById(R.id.exchange2_ask_value);
        exchange2BidValue = (TextView)findViewById(R.id.exchange2_bid_value);

        requestButton = (Button)findViewById(R.id.request_button);
        requestButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                progressLoader.setVisibility(View.VISIBLE);
                requestCurrentExchanges();
                executePossibleTrades();
            }
        });
        progressLoader = (ProgressBar)findViewById(R.id.progress_loader);
        progressLoader.setVisibility(View.INVISIBLE);
    }

    public void onSettingsComplete() {
        checkInfinite = (CheckBox)findViewById(R.id.check_infinite);
        checkInfinite.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                //is chkIos checked?
                if (((CheckBox) v).isChecked()) {
                    //startInfinite(); nope
                }
            }
        });
        differenceChooser = findViewById(R.id.difference_chooser);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, differences);
        differenceChooser.setAdapter(adapter);
        differenceChooser.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int position, long id) {
                chosenDifference = Integer.parseInt((String)parent.getItemAtPosition(position));
            }
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
    }

    public void OnRequestCompleted() {
        requestsCompleted++;
        if(requestsCompleted == 2) {
            progressLoader.setVisibility(View.INVISIBLE);
            requestsCompleted=0;
        }
        startCountAnimation();
        refreshGraph();
    }

    public void requestCurrentExchanges() {

        String result;

        HttpGetRequest getRequest1 = new HttpGetRequest(MainActivity.this);
        try {
            result = getRequest1.execute(exchange1_url).get();
            JSONObject obj = new JSONObject(result).getJSONObject("result").getJSONObject("XXBTZUSD");
            exchange1Ask = obj.getJSONArray("a").getInt(0);
            exchange1Bid = obj.getJSONArray("b").getInt(0);
            exchange1AskValue.setText(Integer.toString(exchange1Ask));
            exchange1BidValue.setText(Integer.toString(exchange1Bid));

            selectedWhich = (RadioButton)findViewById(radioWhich.getCheckedRadioButtonId());
            if(selectedWhich.getText() == getString(R.string.radio_Ka_Bb_text))
                insertToCurrent("ask",exchange1Ask);
            else
                insertToCurrent("bid",exchange1Bid);
        } catch (InterruptedException ie) {

        } catch (ExecutionException ee) {

        } catch (JSONException je) {

        }

        HttpGetRequest getRequest2 = new HttpGetRequest(MainActivity.this);
        try {
            result = getRequest2.execute(exchange2_url).get();
            JSONObject obj = new JSONObject(result);
            exchange2Ask = obj.getInt("ask");
            exchange2Bid = obj.getInt("bid");
            exchange2AskValue.setText(Integer.toString(exchange2Ask));
            exchange2BidValue.setText(Integer.toString(exchange2Bid));

            selectedWhich = (RadioButton)findViewById(radioWhich.getCheckedRadioButtonId());
            if(selectedWhich.getText() == getString(R.string.radio_Ba_Kb_text))
                insertToCurrent("ask",exchange2Ask);
            else
                insertToCurrent("bid",exchange2Bid);
        } catch (InterruptedException ie) {

        } catch (ExecutionException ee) {

        } catch (JSONException je) {

        }
    }

    public void executePossibleTrades() {
        if(exchange1Bid > exchange2Ask + chosenDifference) {
            exchange1BidValue.setTextColor(getColor(R.color.colorAccent));
            exchange2AskValue.setTextColor(getColor(R.color.colorAccent));
            // sell on 1, buy on 2
            walletUSDvalue0 = walletUSDvalue;
            walletUSDvalue += exchange1Bid-exchange2Ask;
            startCountAnimation();
        } else if(exchange2Bid > exchange1Ask + chosenDifference) {
            exchange2BidValue.setTextColor(getColor(R.color.colorAccent));
            exchange1AskValue.setTextColor(getColor(R.color.colorAccent));
            // sell on 2, buy on 1
            walletUSDvalue0 = walletUSDvalue;
            walletUSDvalue += exchange2Bid-exchange1Ask;
            startCountAnimation();
        }
        progressLoader.setVisibility(View.INVISIBLE);
    }

    public void startInfinite() {
        while(true) {
            requestCurrentExchanges();
            executePossibleTrades();
            SystemClock.sleep(5000);
        }
    }

    public void insertToCurrent(String which, int value) {
        if(which == "bid") {
            if (currentBidValuesLength < 10) {
                currentBidValues[currentBidValuesLength] = value;
                currentBidValuesLength++;
                return;
            }
            for (int i = 0; i < 9; i++)
                currentBidValues[i] = currentBidValues[i + 1];
            currentBidValues[9] = value;
        } else {
            if (currentAskValuesLength < 10) {
                currentAskValues[currentAskValuesLength] = value;
                currentAskValuesLength++;
                return;
            }
            for (int i = 0; i < 9; i++)
                currentAskValues[i] = currentAskValues[i + 1];
            currentAskValues[9] = value;
        }
    }
    public DataPoint[] generateData(String which) {
        if(which == "bid") {
            DataPoint[] values = new DataPoint[currentBidValuesLength];
            for (int i = 0; i < currentBidValuesLength; i++) {
                double x = i;
                double y = currentBidValues[i];
                values[i] = new DataPoint(x, y);
            }
            return values;
        } else {
            DataPoint[] values = new DataPoint[currentAskValuesLength];
            for (int i = 0; i < currentAskValuesLength; i++) {
                double x = i;
                double y = currentAskValues[i];
                values[i] = new DataPoint(x, y);
            }
            return values;
        }
    }
    public void refreshGraph() {
        bidSeries.resetData(generateData("bid"));
        askSeries.resetData(generateData("ask"));
    }

    public void startCountAnimation() {
        ValueAnimator animator = ValueAnimator.ofInt(walletUSDvalue0, walletUSDvalue);
        animator.setDuration(1000);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                usdBalanceValue.setText(animation.getAnimatedValue().toString());
            }
        });
        animator.start();
    }


    public static class MyAdapter extends FragmentPagerAdapter {
        public MyAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0: return new WalletFragment();
                case 1: return new RatesFragment();
                case 2: return new SettingsFragment();
            }
            return null;
        }
    }
}
