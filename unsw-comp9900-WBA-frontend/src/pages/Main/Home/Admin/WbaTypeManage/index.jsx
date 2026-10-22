import {useState, useEffect} from 'react';
import './index.css';
import {Loading} from "@/components/Loading/index.jsx";
import BaseModal from "@/components/BaseModal/index.jsx";
import {notify} from "@/utils/notify.js";
import {
    changeWbaTypeStatus, delWbaType, downloadDicImportTemplate,
    getWbaTypeList, importBatchWbaTypes,
    saveWbaTypes
} from "@/config/request/api/dictionaryApi.js";
import {useConfirmStore} from "@/config/store/useConfirmStore.js";
import {downloadFile} from "@/utils/fileUtils.js";
import {FileUploader} from "@/components/FileUploader/index.jsx";
import {FileSpreadsheetIcon} from "lucide-react";

const WbaTypeManage = () => {
    // List data and loading states
    const [wbaTypeList, setWbaTypeList] = useState([]);

    // Filter toolbar states
    const [pageNum, setPageNum] = useState(1);
    const [pageQuantity, setPageQuantity] = useState(10);
    const [total, setTotal] = useState(0);
    const [totalPage, setTotalPage] = useState(1);
    const [searchName, setSearchName] = useState('');
    const [modalOpen, setModalOpen] = useState(false);
    const [importModalOpen, setImportModalOpen] = useState(false);
    const [isSaving, setIsSaving] = useState(false);
    const [selectedWbaType, setSelectedWbaType] = useState({});
    const [selectedFile, setSelectedFile] = useState(null);

    const showConfirm = useConfirmStore(state => state.showConfirm);

    const fetchWbaTypeList = (
        params = {
            pageNum,
            pageQuantity
        }
    ) => {
        Loading.show()
        getWbaTypeList(params)
            .then(data => {
                setPageNum(data.data.current)
                setPageQuantity(data.data.size)
                setTotal(data.data.total)
                setTotalPage(data.data.pages)
                setWbaTypeList(data.data.records);
            })
            .finally(() => Loading.hide());
    };

    useEffect(() => {
        fetchWbaTypeList();
    }, []);

    const handleSearch = () => {
        const params = {
            pageNum: 1,
            pageQuantity,
            name: searchName
        }
        fetchWbaTypeList(params)
    }

    const handleReset = () => {
        setSearchName('');
        const params = {
            pageNum: 1,
            pageQuantity,
        }
        fetchWbaTypeList(params);
    }

    const handlePageChange = newPageNum => {
        setPageNum(newPageNum);
        const params = {
            pageNum: newPageNum,
            pageQuantity,
            name: searchName
        }
        fetchWbaTypeList(params)
    }

    const handleChangeStatus = (id, targetStatus) => {
        changeWbaTypeStatus(id, targetStatus).then(() => {
            notify.success("Change status successfully")
            fetchWbaTypeList()
        })
    }

    const handleDel = (id) => {
        showConfirm(
            'Reminder',
            'Are you sure to confirm?',
            () => {
                delWbaType(id).then(() => {
                    notify.success("Delete successfully")
                    fetchWbaTypeList()
                })
            }
        )
    }

    const handleDownload = () => {
        downloadDicImportTemplate().then(res => {
            downloadFile(res.data, "dic_import_template.xlsx");
        })
    }

    const handleUpload = () => {
        if (!selectedFile) {
            notify.warning("Please select a file to import")
            return;
        }

        Loading.show({text: "Importing wba types", targetId: 'body'})
        setIsSaving(true)

        const formData = new FormData();
        formData.append("file", selectedFile);

        importBatchWbaTypes(formData).then(res => {
            notify.success(`Import ${res.data} wba types. If the number of wba types imported is less than users in the Excel, please check if the name already exist.`)
            closeImportModal()
            fetchWbaTypeList()
        }).finally(() => {
            Loading.hide()
            setIsSaving(false)
        })
    }

    const openModal = data => {
        setModalOpen(true);
        setSelectedWbaType(data)
    };

    const closeModal = () => {
        setModalOpen(false);
    };

    const openImportModal = () => {
        setModalOpen(false)
        setImportModalOpen(true);
    };

    const closeImportModal = () => {
        setImportModalOpen(false);
    };

    const submitEdit = (e) => {
        e.preventDefault();

        setIsSaving(true)

        const data = {
            id: selectedWbaType.id,
            name: e.target.elements.name.value,
        }

        saveWbaTypes(data).then(() => {
            notify.success("Edit the wbaType successfully")
            closeModal()
            fetchWbaTypeList()
        }).finally(() => {
            setIsSaving(false)
        })
    };

    return (
        <div className="dictionary-page-container">
            {/* Header Section */}
            <div className="dictionary-page-header">
                <h1>WbaTypes</h1>
                <p className="dictionary-record-count">{total} wba types</p>
            </div>

            {/* Filter Toolbar */}
            <div className="dictionary-filter-row">
                <div className="dictionary-filter-group">
                    <span className="dictionary-filter-icon">🔍</span>
                    <input
                        type="text"
                        placeholder="Search name..."
                        value={searchName}
                        onChange={(e) => setSearchName(e.target.value)}
                        className="dictionary-input"
                    />
                </div>
                <div className="dictionary-btn-group">
                    <button onClick={handleSearch} className="dictionary-btn-search">Search</button>
                    <button onClick={handleReset} className="dictionary-btn-reset">Reset</button>
                    <button onClick={() => openModal({})} className="dictionary-btn-add">Add</button>
                </div>
            </div>

            {/* Data Table */}
            <div className="dictionary-table-card">
                <table className="dictionary-compact-table">
                    <thead>
                    <tr>
                        <th>WBA Type</th>
                        <th>Creator</th>
                        <th>Status</th>
                        <th>Create Time</th>
                        <th>Edit Time</th>
                        <th className={'dictionary-actionTh'}>Action</th>
                    </tr>
                    </thead>
                    <tbody>
                    {wbaTypeList.length > 0 ? (
                        wbaTypeList.map(wbaType => (
                            <tr key={wbaType.id} className={wbaType.isEnable ? '' : 'dictionary-trDisable'}>
                                <td>{wbaType.name || 'N/A'}</td>
                                <td>{wbaType.creatorName || 'N/A'}</td>
                                <td>
                                    <span className={`dictionary-status-pill ${wbaType.isEnable ? 'enable' : 'disable'}`}>
                                        {wbaType.isEnable ? 'Enable' : 'Disable'}
                                    </span>
                                </td>
                                <td>{wbaType.createTime || 'N/A'}</td>
                                <td>{wbaType.editTime || 'N/A'}</td>
                                <td>
                                    <div className={'dictionary-actionTd'}>
                                        <button
                                            type="button"
                                            role="switch"
                                            className={`dictionary-settings-switch ${wbaType.isEnable ? "on" : "]"}`}
                                            onClick={() => handleChangeStatus(wbaType.id, !wbaType.isEnable)}
                                        >
                                            <span className="dictionary-settings-switch-thumb" />
                                        </button>
                                        <button
                                            className="dictionary-list-button"
                                            onClick={() => openModal(wbaType)}
                                        >
                                            Edit
                                        </button>
                                        <button
                                            className="dictionary-list-button del"
                                            onClick={() => handleDel(wbaType.id)}
                                        >
                                            Del
                                        </button>
                                    </div>
                                </td>
                            </tr>
                        ))
                    ) : (
                        <tr>
                            <td colSpan="6" className="dictionary-no-data">No data found in database.</td>
                        </tr>
                    )}
                    </tbody>
                </table>
            </div>

            {/* Pagination Footer */}
            <div className="dictionary-pagination-bar">
                <button className="dictionary-page-btn" disabled={pageNum === 1} onClick={() => handlePageChange(pageNum - 1)}>Previous</button>
                <span className="dictionary-page-info">Page {pageNum} of {totalPage}</span>
                <button className="dictionary-page-btn" disabled={pageNum === totalPage} onClick={() => handlePageChange(pageNum + 1)}>Next</button>
            </div>

            <BaseModal
                isOpen={modalOpen}
                title="Add/Edit WBA type"
                onClose={closeModal}
            >
                <form onSubmit={submitEdit} className="dictionary-edit-card">
                    <div className="dictionary-edit-heading">
                        <button
                            type="button"
                            className="dictionary-list-button"
                            onClick={() => openImportModal()}
                        >
                            Import Batch
                        </button>
                    </div>

                    <label className="dictionary-edit-label" htmlFor="edit-name">
                        Name
                        <input
                            id="edit-name"
                            name="name"
                            type="text"
                            className="dictionary-edit-input"
                            defaultValue={selectedWbaType?.name}
                            required
                        />
                    </label>

                    <div className="dictionary-edit-actions">
                        <button
                            type="button"
                            onClick={closeModal}
                            className="dictionary-cancel-button"
                            disabled={isSaving}
                        >
                            Cancel
                        </button>

                        <button
                            type="submit"
                            className="dictionary-submit-button"
                            disabled={isSaving}
                        >
                            {isSaving ? "Saving..." : "Save Changes"}
                        </button>
                    </div>
                </form>
            </BaseModal>

            <BaseModal
                isOpen={importModalOpen}
                title="Import batch"
                onClose={closeImportModal}
            >
                <FileUploader
                    // Accept Excel by default
                    icon={<FileSpreadsheetIcon className="w-8 h-8 text-emerald-600" />}
                    onFileSelect={file => setSelectedFile(file)}
                />
                <div className="dictionary-edit-actions">
                    <button
                        type="button"
                        className="dictionary-upload-button"
                        disabled={isSaving}
                        onClick={() => handleUpload()}
                    >
                        {isSaving ? "Uploading..." : "Upload"}
                    </button>
                    <button
                        type="button"
                        className="dictionary-upload-button"
                        disabled={isSaving}
                        onClick={() => handleDownload()}
                    >
                        {isSaving ? "Downloading..." : "Download template"}
                    </button>
                </div>
            </BaseModal>
        </div>
    );
};

export default WbaTypeManage;
