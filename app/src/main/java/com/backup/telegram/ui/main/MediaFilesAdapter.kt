package com.backup.telegram.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.backup.telegram.R
import com.backup.telegram.data.local.entity.MediaFileEntity
import com.backup.telegram.data.local.entity.UploadStatus
import com.backup.telegram.databinding.ItemMediaFileBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MediaFilesAdapter : ListAdapter<MediaFileEntity, MediaFilesAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val binding: ItemMediaFileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MediaFileEntity) {
            binding.fileName.text = item.displayName
            binding.fileSize.text = formatSize(item.sizeBytes)
            binding.fileDate.text = formatDate(item.dateAdded)
            binding.errorText.text = item.errorMessage ?: ""

            val (labelRes, colorRes, bgRes) = when (item.status) {
                UploadStatus.UPLOADED -> Triple(
                    R.string.status_uploaded, R.color.status_green, R.drawable.bg_status_green
                )
                UploadStatus.UPLOADING -> Triple(
                    R.string.status_uploading, R.color.status_blue, R.drawable.bg_status_blue
                )
                UploadStatus.PENDING -> Triple(
                    R.string.status_pending, R.color.status_orange, R.drawable.bg_status_orange
                )
                UploadStatus.FAILED -> Triple(
                    R.string.status_failed, R.color.status_red, R.drawable.bg_status_red
                )
                UploadStatus.SKIPPED -> Triple(
                    R.string.status_skipped, R.color.status_gray, R.drawable.bg_status_gray
                )
            }

            binding.statusBadge.setText(labelRes)
            binding.statusBadge.setTextColor(
                ContextCompat.getColor(binding.root.context, colorRes)
            )
            binding.statusBadge.setBackgroundResource(bgRes)

            // أيقونة نوع الملف
            binding.fileTypeIcon.setImageResource(
                if (item.mimeType.startsWith("video/")) R.drawable.ic_video
                else R.drawable.ic_image
            )

            // إظهار رسالة الخطأ فقط للملفات الفاشلة
            binding.errorText.visibility = if (
                item.status == UploadStatus.FAILED && !item.errorMessage.isNullOrBlank()
            ) android.view.View.VISIBLE else android.view.View.GONE
        }

        private fun formatSize(bytes: Long): String = when {
            bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
            bytes >= 1024      -> "%.0f KB".format(bytes / 1024.0)
            else               -> "$bytes B"
        }

        private fun formatDate(timestamp: Long): String =
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMediaFileBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<MediaFileEntity>() {
            override fun areItemsTheSame(a: MediaFileEntity, b: MediaFileEntity) =
                a.contentUri == b.contentUri

            override fun areContentsTheSame(a: MediaFileEntity, b: MediaFileEntity) = a == b
        }
    }
}
