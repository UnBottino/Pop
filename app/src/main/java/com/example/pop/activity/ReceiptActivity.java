package com.example.pop.activity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.text.Layout;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.pop.DBConstants;
import com.example.pop.R;
import com.example.pop.activity.adapter.ItemListAdapter;
import com.example.pop.activity.adapter.ReceiptListAdapter;
import com.example.pop.helper.HttpJsonParser;
import com.example.pop.model.Item;
import com.example.pop.model.Receipt;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReceiptActivity extends AppCompatActivity {

    private TextView total; //Can currently be got from db
    private TextView cash;
    private TextView change;
    private TextView location; //Temporarily Vendor
    private TextView date; //Can currently be got from db
    private TextView time; //Can currently be got from db
    private TextView barcodeNumber;
    private TextView otherNumber;

    private RecyclerView mRecyclerView;
    private ItemListAdapter mAdapter;
    public List<Item> mItemList = new ArrayList<>();

    int receiptId;
    private int success;
    private String message;

    private Context context;
    private Session session;

    public Receipt receipt;

    private Bitmap bitmap;
    private Button btn_export;

    ConstraintLayout relativeLayout;

    private int STORAGE_PERMISSION_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receipt);

        relativeLayout = findViewById(R.id.receiptLayout);

        btn_export = (Button) findViewById(R.id.export_btn);

        btn_export.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                requestStoragePermission();

                bitmap = Bitmap.createBitmap(relativeLayout.getWidth(), relativeLayout.getHeight(),
                        Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                relativeLayout.draw((canvas));

                String root = Environment.getExternalStorageDirectory().toString();
                File myDir = new File(root + "/PopReceipts");
                myDir.mkdirs();
                String fname = "Receipt_"+ System.currentTimeMillis() +".jpg";
                File file = new File(myDir, fname);

                if (!file.exists()) {
                    Log.d("path", file.toString());

                    try {
                        FileOutputStream fos = new FileOutputStream(file);
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                        Toast.makeText(getApplicationContext(), "Receipt successfully exported!", Toast.LENGTH_SHORT).show();
                        fos.flush();
                        fos.close();
                    } catch (java.io.IOException e) {
                        Toast.makeText(getApplicationContext(), "Problem exporting receipt", Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    }
                }
            }
        });
        context = this;

        Intent intent = getIntent();
        receiptId = intent.getIntExtra("receiptID",0);

        total = findViewById(R.id.receiptTotal);
        cash = findViewById(R.id.receiptCash);
        change = findViewById(R.id.receiptChangeDue);
        location = findViewById(R.id.receiptLocation);
        date = findViewById(R.id.receiptDate);
        time = findViewById(R.id.receiptTime);
        barcodeNumber = findViewById(R.id.receiptBarcodeNumber);
        otherNumber = findViewById(R.id.receiptOtherNumber);

        new FetchReceiptsInfoAsyncTask().execute();
    }

    private void requestStoragePermission() {
        if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            new AlertDialog.Builder(context)
                    .setTitle("Permission needed")
                    .setMessage("This permission is needed to export your receipts.")
                    .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            ActivityCompat.requestPermissions(ReceiptActivity.this, new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
                        }
                    })
                    .setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.dismiss();
                        }
                    })
                    .create().show();
        } else {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == STORAGE_PERMISSION_CODE) {
            if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            } else {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_LONG).show();
            }
        }
    }

    private class FetchReceiptsInfoAsyncTask extends AsyncTask<String, String, String> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected String doInBackground(String... params) {
            HttpJsonParser httpJsonParser = new HttpJsonParser();
            Map<String, String> httpParams = new HashMap<>();
            httpParams.put("receipt_id", String.valueOf(receiptId));
            JSONObject jsonObject = httpJsonParser.makeHttpRequest(DBConstants.BASE_URL + "getAllReceiptInfo.php", "POST", httpParams);

            try {
                success = jsonObject.getInt("success");
                JSONArray receiptData;
                JSONArray itemData;

                if (success == 1) {
                    receiptData = jsonObject.getJSONArray("receipt");
                    itemData = jsonObject.getJSONArray("items");
                    for (int i = 0; i < receiptData.length(); i++) {
                        JSONObject receiptInfo = receiptData.getJSONObject(i);

                        //Values that can currently be gotten from database and assigned to receipt
                        int receiptId = receiptInfo.getInt(DBConstants.RECEIPT_ID);
                        String receiptDate = receiptInfo.getString(DBConstants.DATE);
                        String receiptTime = receiptInfo.getString("time");
                        String receiptVendor = receiptInfo.getString(DBConstants.VENDOR);
                        double receiptTotal = receiptInfo.getDouble(DBConstants.RECEIPT_TOTAL);

                        receipt = new Receipt(receiptId, receiptDate, receiptTime, receiptVendor, receiptTotal);
                    }

                    for (int i = 0; i < itemData.length(); i++) {
                        JSONObject item = itemData.getJSONObject(i);
                        int itemId = item.getInt(DBConstants.ITEM_ID);
                        String itemName = item.getString("name");
                        double itemPrice = item.getDouble(DBConstants.PRICE);
                        int itemQuantity = item.getInt(DBConstants.QUANTITY);

                        //Populate a list of items to be displayed on receipt
                        mItemList.add(new Item(itemId,itemName,itemPrice,itemQuantity));
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }

        protected void onPostExecute(String result) {
            if(success == 1)
            {
                location.setText(receipt.getVendorName());
                date.setText(receipt.getDate());
                time.setText(receipt.getTime());
                total.setText("Total: €" + String.format("%.2f", receipt.getReceiptTotal()));
            }
            else{
                Toast.makeText(ReceiptActivity.this,"Empty", Toast.LENGTH_LONG).show();
            }
            mRecyclerView = findViewById(R.id.itemList);
            mAdapter = new ItemListAdapter(context, mItemList);
            mRecyclerView.setAdapter(mAdapter);
            mRecyclerView.setLayoutManager(new LinearLayoutManager(context));
        }
    }
}
