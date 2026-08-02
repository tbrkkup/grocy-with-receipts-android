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

import android.util.Log;
import androidx.annotation.Nullable;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import xyz.zedler.patrick.grocy.helper.DownloadHelper;
import xyz.zedler.patrick.grocy.helper.DownloadHelper.OnErrorListener;
import xyz.zedler.patrick.grocy.helper.DownloadHelper.OnMultiTypeErrorListener;
import xyz.zedler.patrick.grocy.helper.DownloadHelper.OnObjectsResponseListener;
import xyz.zedler.patrick.grocy.helper.DownloadHelper.OnStringResponseListener;
import xyz.zedler.patrick.grocy.web.NetworkQueue.QueueItem;

public class ProductsPriceHistory {

  @SerializedName("id")
  private int id;

  @SerializedName("product_id")
  private int productId;

  @SerializedName("amount")
  private double amount;

  @SerializedName("price")
  private Double price;

  @SerializedName("transaction_type")
  private String transactionType;

  @SerializedName("purchased_date")
  private String purchasedDate;

  @SerializedName("shopping_location_id")
  private Integer shoppingLocationId;

  public int getId() {
    return id;
  }

  public int getProductId() {
    return productId;
  }

  public double getAmount() {
    return amount;
  }

  public Double getPrice() {
    return price;
  }

  public String getTransactionType() {
    return transactionType;
  }

  public String getPurchasedDate() {
    return purchasedDate;
  }

  public Integer getShoppingLocationId() {
    return shoppingLocationId;
  }

  public double getTotalCost() {
    if (price == null) return 0;
    return amount * price;
  }

  public static QueueItem getPriceHistory(
      DownloadHelper dlHelper,
      String startDate,
      String endDate,
      OnObjectsResponseListener<ProductsPriceHistory> onResponseListener,
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
            dlHelper.grocyApi.getProductsPriceHistory(startDate, endDate),
            uuid,
            response -> {
              Type type = new TypeToken<ArrayList<ProductsPriceHistory>>() {}.getType();
              ArrayList<ProductsPriceHistory> items = dlHelper.gson.fromJson(response, type);
              if (dlHelper.debug) {
                Log.i(dlHelper.tag, "download ProductsPriceHistory: " + items);
              }
              if (onResponseListener != null) onResponseListener.onResponse(items);
              if (responseListener != null) responseListener.onResponse(response);
            },
            error -> {
              if (onErrorListener != null) onErrorListener.onError(error);
              if (errorListener != null) errorListener.onError(error);
            }
        );
      }
    };
  }
}
