package com.zero.drive.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.api.services.drive.model.File
import com.zero.drive.databinding.ItemFilesBinding

class FilesAdapter(private val context: Context, private var filesList: List<File?>,
                   private val onDownloadRequested: (Int, File?) -> Unit):
    RecyclerView.Adapter<FilesAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemFilesBinding): RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFilesBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int {
        return filesList.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = filesList[position]

        holder.binding.tvName.text = file?.name
        holder.binding.ivDownload.setOnClickListener { onDownloadRequested.invoke(position, file) }
    }

    fun updateData(files: List<File?>) {
        filesList = files
        notifyDataSetChanged()
    }

}