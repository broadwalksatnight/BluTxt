namespace BluTxt
{
    partial class Form1
    {
        /// <summary>
        ///  Required designer variable.
        /// </summary>
        private System.ComponentModel.IContainer components = null;

        /// <summary>
        ///  Clean up any resources being used.
        /// </summary>
        /// <param name="disposing">true if managed resources should be disposed; otherwise, false.</param>
        protected override void Dispose(bool disposing)
        {
            if (disposing && (components != null))
            {
                components.Dispose();
            }
            base.Dispose(disposing);
        }

        #region Windows Form Designer generated code

        /// <summary>
        ///  Required method for Designer support - do not modify
        ///  the contents of this method with the code editor.
        /// </summary>
        private void InitializeComponent()
        {
            System.ComponentModel.ComponentResourceManager resources = new System.ComponentModel.ComponentResourceManager(typeof(Form1));
            btnScan = new System.Windows.Forms.Button();
            lblStatus = new System.Windows.Forms.Label();
            textBoxLog = new System.Windows.Forms.TextBox();
            listBoxDevices = new System.Windows.Forms.ListBox();
            textBoxSend = new System.Windows.Forms.TextBox();
            textBoxChat = new System.Windows.Forms.RichTextBox();
            SuspendLayout();
            // 
            // btnScan
            // 
            btnScan.Location = new System.Drawing.Point(447, 48);
            btnScan.Name = "btnScan";
            btnScan.Size = new System.Drawing.Size(165, 27);
            btnScan.TabIndex = 0;
            btnScan.Text = "Scan";
            btnScan.UseVisualStyleBackColor = true;
            btnScan.Click += btnScan_Click;
            // 
            // lblStatus
            // 
            lblStatus.AutoSize = true;
            lblStatus.Location = new System.Drawing.Point(21, 25);
            lblStatus.Name = "lblStatus";
            lblStatus.Size = new System.Drawing.Size(49, 20);
            lblStatus.TabIndex = 2;
            lblStatus.Text = "Status";
            // 
            // textBoxLog
            // 
            textBoxLog.Location = new System.Drawing.Point(21, 48);
            textBoxLog.Name = "textBoxLog";
            textBoxLog.Size = new System.Drawing.Size(420, 27);
            textBoxLog.TabIndex = 3;
            // 
            // listBoxDevices
            // 
            listBoxDevices.FormattingEnabled = true;
            listBoxDevices.ItemHeight = 20;
            listBoxDevices.Location = new System.Drawing.Point(447, 87);
            listBoxDevices.Name = "listBoxDevices";
            listBoxDevices.Size = new System.Drawing.Size(165, 104);
            listBoxDevices.TabIndex = 4;
            listBoxDevices.MouseDoubleClick += listBoxDevices_MouseDoubleClick;
            // 
            // textBoxSend
            // 
            textBoxSend.Location = new System.Drawing.Point(21, 377);
            textBoxSend.Name = "textBoxSend";
            textBoxSend.Size = new System.Drawing.Size(420, 27);
            textBoxSend.TabIndex = 6;
            textBoxSend.KeyDown += textBoxSend_KeyDown;
            // 
            // textBoxChat
            // 
            textBoxChat.Location = new System.Drawing.Point(21, 87);
            textBoxChat.Name = "textBoxChat";
            textBoxChat.Size = new System.Drawing.Size(420, 264);
            textBoxChat.TabIndex = 7;
            textBoxChat.Text = "";
            // 
            // Form1
            // 
            AutoScaleDimensions = new System.Drawing.SizeF(8F, 20F);
            AutoScaleMode = System.Windows.Forms.AutoScaleMode.Font;
            ClientSize = new System.Drawing.Size(645, 449);
            Controls.Add(textBoxChat);
            Controls.Add(textBoxSend);
            Controls.Add(listBoxDevices);
            Controls.Add(textBoxLog);
            Controls.Add(lblStatus);
            Controls.Add(btnScan);
            FormBorderStyle = System.Windows.Forms.FormBorderStyle.FixedSingle;
            Icon = (System.Drawing.Icon)resources.GetObject("$this.Icon");
            Name = "Form1";
            Text = "BluTxt";
            ResumeLayout(false);
            PerformLayout();
        }

        #endregion

        private System.Windows.Forms.Button btnScan;
        private System.Windows.Forms.Label lblStatus;
        private System.Windows.Forms.TextBox textBoxLog;
        private System.Windows.Forms.ListBox listBoxDevices;
        private System.Windows.Forms.TextBox textBoxSend;
        private System.Windows.Forms.RichTextBox textBoxChat;
    }
}
