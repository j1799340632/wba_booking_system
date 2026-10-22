import {useRef, useState, useId} from 'react';
import {motion, AnimatePresence} from 'framer-motion';
import {UploadCloud, File, X} from 'lucide-react';
import './index.css';
import {notify} from "@/utils/notify.js";

export const FileUploader = (
    {
        onFileSelect,
        onFileRemove,
        accept = '.xlsx, .xls, .csv, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet, application/vnd.ms-excel',
        hintText = 'Only support .xlsx / .xls / .csv',
        icon = <UploadCloud size={32}/>,
        maxSizeMB = 10,
        disabled = false,
        className = '',
    }
) => {
    const [isDragOver, setIsDragOver] = useState(false);
    const [selectedFile, setSelectedFile] = useState(null);
    const inputRef = useRef(null);
    const inputId = useId();

    // Format validation
    const validateFile = (file) => {
        if (!file) return false;
        if (!accept) return true;

        const fileName = file.name.toLowerCase();
        const fileType = file.type.toLowerCase();

        const rules = accept
            .split(',')
            .map((r) => r.trim().toLowerCase())
            .filter(Boolean);

        return rules.some((rule) => {
            if (rule.startsWith('.')) {
                return fileName.endsWith(rule);
            } else if (rule.endsWith('/*')) {
                const category = rule.split('/')[0];
                return fileType.startsWith(`${category}/`);
            } else {
                return fileType === rule;
            }
        });
    };

    const validateAndProcessFile = (file) => {
        if (!file) return;

        if (!validateFile(file)) {
            notify.error('File format is not supported')
            return;
        }

        const isOverSize = file.size > maxSizeMB * 1024 * 1024;
        if (isOverSize) {
            notify.error(`File size cannot exceed ${maxSizeMB}MB`)
            return;
        }

        setSelectedFile(file);
        onFileSelect?.(file);
        notify.success('File read successfully, please click "Upload" to proceed');
    };

    const handleDragOver = (e) => {
        e.preventDefault();
        e.stopPropagation();
        if (!disabled) setIsDragOver(true);
    };

    const handleDragLeave = (e) => {
        e.preventDefault();
        e.stopPropagation();
        setIsDragOver(false);
    };

    const handleDrop = (e) => {
        e.preventDefault();
        e.stopPropagation();
        setIsDragOver(false);

        if (disabled) return;
        const files = e.dataTransfer.files;
        if (files && files.length > 0) {
            validateAndProcessFile(files[0]);
        }
    };

    const handleInputChange = (e) => {
        const files = e.target.files;
        if (files && files.length > 0) {
            validateAndProcessFile(files[0]);
        }
    };

    const handleRemove = (e) => {
        e.stopPropagation();
        setSelectedFile(null);
        if (inputRef.current) inputRef.current.value = '';
        onFileRemove?.();
    };

    const formatFileSize = (bytes) => {
        if (bytes === 0) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    };

    return (
        <div className={`file-uploader-container ${className}`}>
            <input
                ref={inputRef}
                id={inputId}
                type="file"
                accept={accept}
                onChange={handleInputChange}
                disabled={disabled}
                className="file-uploader-input"
            />

            {/* Drag file box */}
            <motion.label
                htmlFor={inputId}
                onDragOver={handleDragOver}
                onDragLeave={handleDragLeave}
                onDrop={handleDrop}
                whileHover={disabled ? {} : {scale: 1.01}}
                whileTap={disabled ? {} : {scale: 0.99}}
                className={`file-uploader-dropzone ${isDragOver ? 'dragover' : ''} ${disabled ? 'disabled' : ''}`}
            >
                <motion.div
                    animate={{scale: isDragOver ? 1.15 : 1}}
                    className="file-uploader-icon-wrapper"
                >
                    {icon}
                </motion.div>

                <div className="file-uploader-text-group">
                    <p className="file-uploader-title">
                        {isDragOver ? 'Release to place the file' : 'Click to upload or drag the file here'}
                    </p>
                    <p className="file-uploader-hint">
                        {hintText}（Max size {maxSizeMB}MB）
                    </p>
                </div>
            </motion.label>

            {/* Selected file */}
            <AnimatePresence>
                {selectedFile && (
                    <motion.div
                        initial={{opacity: 0, y: 10, scale: 0.95}}
                        animate={{opacity: 1, y: 0, scale: 1}}
                        exit={{opacity: 0, y: -10, scale: 0.95}}
                        transition={{duration: 0.2}}
                        className="file-uploader-preview"
                    >
                        <div className="file-uploader-preview-info">
                            <div className="file-uploader-file-icon">
                                <File size={20}/>
                            </div>
                            <div className="file-uploader-file-details">
                                <span className="file-uploader-file-name">{selectedFile.name}</span>
                                <span className="file-uploader-file-size">
                  {formatFileSize(selectedFile.size)}
                </span>
                            </div>
                        </div>

                        <motion.button
                            type="button"
                            onClick={handleRemove}
                            whileHover={{scale: 1.1}}
                            whileTap={{scale: 0.9}}
                            className="file-uploader-remove-btn"
                            title="Remove file"
                        >
                            <X size={16}/>
                        </motion.button>
                    </motion.div>
                )}
            </AnimatePresence>
        </div>
    );
};
