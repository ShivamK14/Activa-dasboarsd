package com.example.activadasboard.ui.dashboard;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.activadasboard.R;

import java.util.ArrayList;
import java.util.List;

public class RecentOrdersAdapter extends RecyclerView.Adapter<RecentOrdersAdapter.OrderViewHolder> {

    private List<Order> orders = new ArrayList<>();

    public void setOrders(List<Order> orders) {
        this.orders = orders;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recent_order, parent, false);
        return new OrderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
        Order order = orders.get(position);
        holder.orderId.setText("Order #" + order.getId());
        holder.orderDate.setText(order.getDate());
        holder.orderAmount.setText("₹" + order.getAmount());
    }

    @Override
    public int getItemCount() {
        return orders.size();
    }

    static class OrderViewHolder extends RecyclerView.ViewHolder {
        TextView orderId;
        TextView orderDate;
        TextView orderAmount;

        OrderViewHolder(View itemView) {
            super(itemView);
            orderId = itemView.findViewById(R.id.orderId);
            orderDate = itemView.findViewById(R.id.orderDate);
            orderAmount = itemView.findViewById(R.id.orderAmount);
        }
    }

    static class Order {
        private String id;
        private String date;
        private double amount;

        public Order(String id, String date, double amount) {
            this.id = id;
            this.date = date;
            this.amount = amount;
        }

        public String getId() {
            return id;
        }

        public String getDate() {
            return date;
        }

        public double getAmount() {
            return amount;
        }
    }
} 