/*
 * This file is part of Grocy Android.
 *
 * Grocy Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Grocy Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Grocy Android. If not, see http://www.gnu.org/licenses/.
 *
 * Copyright (c) 2020-2024 by Patrick Zedler and Dominic Zedler
 * Copyright (c) 2024-2026 by Patrick Zedler
 */

package xyz.zedler.patrick.grocy.model;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import xyz.zedler.patrick.grocy.Constants;
import xyz.zedler.patrick.grocy.Constants.PREF;
import xyz.zedler.patrick.grocy.api.GrocyApi.ENTITY;
import xyz.zedler.patrick.grocy.helper.DownloadHelper;
import xyz.zedler.patrick.grocy.helper.DownloadHelper.OnErrorListener;
import xyz.zedler.patrick.grocy.helper.DownloadHelper.OnMultiTypeErrorListener;
import xyz.zedler.patrick.grocy.helper.DownloadHelper.OnObjectsResponseListener;
import xyz.zedler.patrick.grocy.helper.DownloadHelper.OnStringResponseListener;
import xyz.zedler.patrick.grocy.web.NetworkQueue.QueueItem;

@Entity(tableName = "receipt_table")
public class Receipt implements Parcelable {

  public final static String STATUS_PAID = "paid";
  public final static String STATUS_OPEN = "open";
  public final static String STATUS_REFUNDED = "refunded";

  @PrimaryKey
  @ColumnInfo(name = "id")
  @SerializedName("id")
  private int id;

  @ColumnInfo(name = "shopping_location_id")
  @SerializedName("shopping_location_id")
  private String shoppingLocationId;

  @ColumnInfo(name = "date")
  @SerializedName("date")
  private String date;

  @ColumnInfo(name = "status")
  @SerializedName("status")
  private String status;

  @ColumnInfo(name = "description")
  @SerializedName("description")
  private String description;

  @ColumnInfo(name = "row_created_timestamp")
  @SerializedName("row_created_timestamp")
  private String rowCreatedTimestamp;

  public Receipt() {
  }  // for Room

  public Receipt(Parcel parcel) {
    id = parcel.readInt();
    shoppingLocationId = parcel.readString();
    date = parcel.readString();
    status = parcel.readString();
    description = parcel.readString();
    rowCreatedTimestamp = parcel.readString();
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(id);
    dest.writeString(shoppingLocationId);
    dest.writeString(date);
    dest.writeString(status);
    dest.writeString(description);
    dest.writeString(rowCreatedTimestamp);
  }

  public static final Creator<Receipt> CREATOR = new Creator<>() {

    @Override
    public Receipt createFromParcel(Parcel in) {
      return new Receipt(in);
    }

    @Override
    public Receipt[] newArray(int size) {
      return new Receipt[size];
    }
  };

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  public String getShoppingLocationId() {
    return shoppingLocationId;
  }

  public void setShoppingLocationId(String shoppingLocationId) {
    this.shoppingLocationId = shoppingLocationId;
  }

  public String getDate() {
    return date;
  }

  public void setDate(String date) {
    this.date = date;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getRowCreatedTimestamp() {
    return rowCreatedTimestamp;
  }

  public void setRowCreatedTimestamp(String rowCreatedTimestamp) {
    this.rowCreatedTimestamp = rowCreatedTimestamp;
  }

  public static Receipt getReceiptFromId(List<Receipt> receipts, int id) {
    for (Receipt receipt : receipts) {
      if (receipt.getId() == id) {
        return receipt;
      }
    }
    return null;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Receipt that = (Receipt) o;
    return id == that.id &&
        Objects.equals(shoppingLocationId, that.shoppingLocationId) &&
        Objects.equals(date, that.date) &&
        Objects.equals(status, that.status) &&
        Objects.equals(description, that.description) &&
        Objects.equals(rowCreatedTimestamp, that.rowCreatedTimestamp);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, shoppingLocationId, date, status, description, rowCreatedTimestamp);
  }

  @NonNull
  @Override
  public String toString() {
    return "Receipt(" + id + ')';
  }

  public static QueueItem getReceipts(
      DownloadHelper dlHelper,
      OnObjectsResponseListener<Receipt> onResponseListener,
      OnErrorListener onErrorListener
  ) {
    return new QueueItem() {
      @Override
      public void perform(
          @Nullable OnStringResponseListener responseListener,
          @Nullable OnMultiTypeErrorListener errorListener,
          @Nullable String uuid
      ) {
        dlHelper.get(
            dlHelper.grocyApi.getObjects(ENTITY.RECEIPTS),
            uuid,
            response -> {
              Type type = new TypeToken<List<Receipt>>() {
              }.getType();
              ArrayList<Receipt> receipts = dlHelper.gson.fromJson(response, type);
              if (dlHelper.debug) {
                Log.i(dlHelper.tag, "download Receipts: " + receipts);
              }
              if (onResponseListener != null) {
                onResponseListener.onResponse(receipts);
              }
              if (responseListener != null) {
                responseListener.onResponse(response);
              }
            },
            error -> {
              if (onErrorListener != null) {
                onErrorListener.onError(error);
              }
              if (errorListener != null) {
                errorListener.onError(error);
              }
            }
        );
      }
    };
  }

  public static QueueItem updateReceipts(
      DownloadHelper dlHelper,
      String dbChangedTime,
      boolean forceUpdate,
      OnObjectsResponseListener<Receipt> onResponseListener
  ) {
    String lastTime = !forceUpdate ? dlHelper.sharedPrefs.getString(
        Constants.PREF.DB_LAST_TIME_RECEIPTS, null
    ) : null;
    if (lastTime == null || !lastTime.equals(dbChangedTime)) {
      return new QueueItem() {
        @Override
        public void perform(
            @Nullable OnStringResponseListener responseListener,
            @Nullable OnMultiTypeErrorListener errorListener,
            @Nullable String uuid
        ) {
          dlHelper.get(
              dlHelper.grocyApi.getObjects(ENTITY.RECEIPTS),
              uuid,
              response -> {
                Type type = new TypeToken<List<Receipt>>() {
                }.getType();
                ArrayList<Receipt> receipts = dlHelper.gson.fromJson(response, type);
                if (dlHelper.debug) {
                  Log.i(dlHelper.tag, "download Receipts: " + receipts);
                }
                Single.fromCallable(() -> {
                  dlHelper.appDatabase.receiptDao()
                      .deleteReceipts().blockingSubscribe();
                  dlHelper.appDatabase.receiptDao()
                      .insertReceipts(receipts).blockingSubscribe();
                  dlHelper.sharedPrefs.edit()
                      .putString(PREF.DB_LAST_TIME_RECEIPTS, dbChangedTime).apply();
                  return true;
                })
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doFinally(() -> {
                      if (onResponseListener != null) {
                        onResponseListener.onResponse(receipts);
                      }
                      if (responseListener != null) {
                        responseListener.onResponse(response);
                      }
                    })
                    .subscribe(ignored -> {}, throwable -> {
                      if (errorListener != null) {
                        errorListener.onError(throwable);
                      }
                    });
              },
              error -> {
                if (errorListener != null) {
                  errorListener.onError(error);
                }
              }
          );
        }
      };
    } else {
      if (dlHelper.debug) {
        Log.i(dlHelper.tag, "downloadData: skipped Receipts download");
      }
      return null;
    }
  }
}
