import {useState, useEffect} from 'react';
import './index.css';
import {
    addUser,
    changeUserStatus,
    downloadUserImportTemplate,
    getUserList,
    importBatchUser,
    resetPassword,
    updateOtherProfile,
    delUser,
} from "@/config/request/api/registrarApi.js";
import {Loading} from "@/components/Loading/index.jsx";
import BaseModal from "@/components/BaseModal/index.jsx";
import {ChangePasswordModal} from "@/components/ChangePasswordModal/index.jsx";
import {getEmailVerificationCode} from "@/config/request/api/verifyApi.js";
import {getEnabledRoleList} from "@/config/request/api/userRoleApi.js";
import {notify} from "@/utils/notify.js";
import {getEnabledDepartments, getEnabledHospitals} from "@/config/request/api/dictionaryApi.js";
import {FileUploader} from "@/components/FileUploader/index.jsx";
import {FileSpreadsheetIcon} from "lucide-react";
import {useConfirmStore} from "@/config/store/useConfirmStore.js";
import {downloadFile} from "@/utils/fileUtils.js";
import {useUserStore} from "@/config/store/useUserStore.js";
import {EMAIL_REGEX} from "@/config/constant/RegexPatternConstant.js";

const UserManage = () => {
    const userInfo = useUserStore((state) => state.userInfo);

    // List data and loading states
    const [users, setUsers] = useState([]);

    // Filter toolbar states
    const [pageNum, setPageNum] = useState(1);
    const [pageQuantity, setPageQuantity] = useState(10);
    const [total, setTotal] = useState(0);
    const [totalPage, setTotalPage] = useState(1);
    const [userRealName, setUserRealName] = useState('');
    const [hospitalFilter, setHospitalFilter] = useState('');
    const [departmentFilter, setDepartmentFilter] = useState('');
    const [modalOpen, setModalOpen] = useState(false);
    const [addModalOpen, setAddModalOpen] = useState(false);
    const [subModalOpen, setSubModalOpen] = useState(false);
    const [importModalOpen, setImportModalOpen] = useState(false);
    const [isSaving, setIsSaving] = useState(false);
    const [selectedUser, setSelectedUser] = useState({});
    const [selectedFile, setSelectedFile] = useState(null);
    const [roleSelectList, setRoleSelectList] = useState([]);
    const [hospitalSelectList, setHospitalSelectList] = useState([]);
    const [departmentSelectList, setDepartmentSelectList] = useState([]);

    const showConfirm = useConfirmStore(state => state.showConfirm);

    const fetchUserList = (
        params = {
            pageNum,
            pageQuantity,
        }
    ) => {
        Loading.show()
        getUserList(params)
            .then(data => {
                setPageNum(data.data.current)
                setPageQuantity(data.data.size)
                setTotal(data.data.total)
                setTotalPage(data.data.pages)
                setUsers(data.data.records);
            })
            .finally(() => Loading.hide());
    };

    useEffect(() => {
        fetchUserList();
        getEnabledHospitals().then(res => setHospitalSelectList(res.data))
        getEnabledDepartments().then(res => setDepartmentSelectList(res.data))
        getEnabledRoleList().then(res => {
            const roleList = res.data

            if (roleList) {
                const filterRoleList = roleList.filter(item => userInfo.permissionPriority <= item.permissionPriority)
                setRoleSelectList(filterRoleList)
            }
        })
    }, []);

    const handleSearch = () => {
        const params = {
            pageNum: 1,
            pageQuantity,
            name: userRealName,
            hospital: hospitalFilter,
            department: departmentFilter,
        }
        fetchUserList(params)
    }

    const handleReset = () => {
        setUserRealName('')
        setHospitalFilter('');
        setDepartmentFilter('');
        const params = {
            pageNum: 1,
            pageQuantity,
        }
        fetchUserList(params);
    }

    const handlePageChange = newPageNum => {
        setPageNum(newPageNum);
        const params = {
            pageNum: newPageNum,
            pageQuantity,
            name: userRealName,
            hospital: hospitalFilter,
            department: departmentFilter,
        }
        fetchUserList(params)
    }

    const handleChangeUserStatus = (userId, targetStatus) => {
        changeUserStatus(userId, targetStatus).then(() => {
            notify.success("Change the user status successfully")
            fetchUserList()
        })
    }

    const handleDel = (id) => {
        showConfirm(
            'Reminder',
            'Are you sure to confirm?',
            () => {
                delUser(id).then(() => {
                    notify.success("Delete successfully")
                    fetchUserList()
                })
            }
        )
    }

    const openModal = user => {
        setModalOpen(true);
        setSelectedUser(user)
    };

    const closeModal = () => {
        setModalOpen(false);
    };

    const openSubModal = () => {
        setSubModalOpen(true);
    };

    const closeSubModal = () => {
        setSubModalOpen(false);
    };

    const openAddModal = () => {
        setAddModalOpen(true);
    };

    const closeAddModal = () => {
        setAddModalOpen(false);
    };

    const openImportModal = () => {
        setAddModalOpen(false)
        setImportModalOpen(true);
    };

    const closeImportModal = () => {
        setImportModalOpen(false);
    };

    const checkEmail = (email) => {
        if (!email || !EMAIL_REGEX.test(email)) {
            notify.warning("Please enter a valid email address")
            setIsSaving(false)
            return false
        }
        return true
    }

    const submitEdit = (e) => {
        e.preventDefault();

        setIsSaving(true)

        const data = {
            id: selectedUser.id,
            email: e.target.elements.email.value,
            roleId: e.target.elements.roleId.value,
            name: e.target.elements.name.value,
        }

        if (!checkEmail(data.email)) {
            return
        }

        updateOtherProfile(data).then(() => {
            notify.success("Edit the user successfully")
            closeModal()
            fetchUserList()
        }).finally(() => {
            setIsSaving(false)
        })
    };

    const submitAdd = (e) => {
        e.preventDefault();

        setIsSaving(true)

        const data = {
            email: e.target.elements.email.value,
            password: e.target.elements.password.value,
            roleId: e.target.elements.roleId.value,
            name: e.target.elements.name.value,
        }

        if (!checkEmail(data.email)) {
            return
        }

        addUser(data).then(() => {
            notify.success("Add a user successfully")
            closeAddModal()
            fetchUserList()
        }).finally(() => {
            setIsSaving(false)
        })
    };

    const handleUpload = () => {
        if (!selectedFile) {
            notify.warning("Please select a file to import")
            return;
        }

        Loading.show({text: "Importing users", targetId: 'body'})
        setIsSaving(true)

        const formData = new FormData();
        formData.append("file", selectedFile);

        importBatchUser(formData).then(res => {
            notify.success(`Import ${res.data} users. If the number of users imported is less than users in the Excel, please check the email format, whether the email is already registered, and whether the role key is correct.`)
            closeImportModal()
            fetchUserList()
        }).finally(() => {
            Loading.hide()
            setIsSaving(false)
        })
    }

    const handleDownload = () => {
        downloadUserImportTemplate().then(res => {
            downloadFile(res.data, "user_import_template.xlsx");
        })
    }

    return (
        <div className="page-container">
            {/* Header Section */}
            <div className="page-header">
                <h1>Users</h1>
                <p className="record-count">{total} users</p>
            </div>

            {/* Filter Toolbar */}
            <div className="filter-row">
                <div className="filter-group">
                    <span className="filter-icon">🔍</span>
                    <input
                        type="text"
                        placeholder="Search name..."
                        value={userRealName}
                        onChange={(e) => setUserRealName(e.target.value)}
                        className="user-input"
                    />
                    <select
                        value={hospitalFilter}
                        onChange={(e) => setHospitalFilter(e.target.value)}
                        className="user-select"
                    >
                        <option value="">
                            Select a hospital
                        </option>
                        {hospitalSelectList.map(
                            (hospital) => (
                                <option
                                    key={hospital.id}
                                    value={hospital.name}
                                >
                                    {hospital.name}
                                </option>
                            )
                        )}
                    </select>
                    <select
                        value={departmentFilter}
                        onChange={(e) => setDepartmentFilter(e.target.value)}
                        className="user-select"
                    >
                        <option value="">Select a departments</option>
                        {departmentSelectList.map(
                            (department) => (
                                <option
                                    key={department.id}
                                    value={department.name}
                                >
                                    {department.name}
                                </option>
                            )
                        )}
                    </select>
                </div>
                <div className="user-btn-group">
                    <button onClick={handleSearch} className="user-btn-search">Search</button>
                    <button onClick={handleReset} className="user-btn-reset">Reset</button>
                    <button onClick={openAddModal} className="user-btn-add">Add</button>
                </div>
            </div>

            {/* Data Table */}
            <div className="table-card">
                <table className="compact-table">
                    <thead>
                    <tr>
                        <th>Name</th>
                        <th>RoleKey</th>
                        <th>Email</th>
                        <th>Hospital</th>
                        <th>Department</th>
                        <th>Status</th>
                        <th className={'user-actionTh'}>Action</th>
                    </tr>
                    </thead>
                    <tbody>
                    {users.length > 0 ? (
                        users.map(user => (
                            <tr key={user.id} className={user.isEnable ? '' : 'user-trDisable'}>
                                <td>
                                    <div className="main-text">{user.name || 'N/A'}</div>
                                    <div className="sub-text">{user.title || 'N/A'}</div>
                                </td>
                                <td>{user.roleKey || 'N/A'}</td>
                                <td>{user.email || 'N/A'}</td>
                                <td>{user.hospital || 'N/A'}</td>
                                <td>{user.department || 'N/A'}</td>
                                <td>
                                    <span className={`status-pill ${user.isEnable ? 'enable' : 'disable'}`}>
                                        {user.isEnable ? 'Enable' : 'Disable'}
                                    </span>
                                </td>
                                <td>
                                    <div className={'user-actionTd'}>
                                        <button
                                            type="button"
                                            role="switch"
                                            className={`user-settings-switch ${user.isEnable ? "on" : "]"}`}
                                            onClick={() => handleChangeUserStatus(user.id, !user.isEnable)}
                                        >
                                            <span className="user-settings-switch-thumb" />
                                        </button>
                                        <button
                                            className="user-list-button"
                                            onClick={() => openModal(user)}
                                        >
                                            Edit
                                        </button>
                                        <button
                                            className="dictionary-list-button del"
                                            onClick={() => handleDel(user.id)}
                                        >
                                            Del
                                        </button>
                                    </div>
                                </td>
                            </tr>
                        ))
                    ) : (
                        <tr>
                            <td colSpan="6" className="user-no-data">No users found in database.</td>
                        </tr>
                    )}
                    </tbody>
                </table>
            </div>

            {/* Pagination Footer */}
            <div className="user-pagination-bar">
                <button className="user-page-btn" disabled={pageNum === 1} onClick={() => handlePageChange(pageNum - 1)}>Previous</button>
                <span className="user-page-info">Page {pageNum} of {totalPage}</span>
                <button className="user-page-btn" disabled={pageNum === totalPage} onClick={() => handlePageChange(pageNum + 1)}>Next</button>
            </div>

            <BaseModal
                isOpen={modalOpen}
                title="Edit User"
                onClose={closeModal}
            >
                <BaseModal
                    isOpen={subModalOpen}
                    title="Change Password"
                    onClose={closeSubModal}
                >
                    <ChangePasswordModal
                        targetUserId={selectedUser.id}
                        onClose={closeSubModal}
                        onSendCode={getEmailVerificationCode}
                        onChangePassword={resetPassword}
                    />
                </BaseModal>
                <form onSubmit={submitEdit} className="user-edit-card">
                    <div className="user-edit-heading">
                        <button
                            type="button"
                            className="user-list-button"
                            onClick={() => openSubModal()}
                        >
                            Change Password
                        </button>
                    </div>

                    <label className="user-edit-label" htmlFor="edit-email">
                        Email
                        <input
                            id="edit-email"
                            name="email"
                            type="email"
                            className="user-edit-input"
                            defaultValue={selectedUser.email}
                            required
                        />
                    </label>

                    <label className="user-edit-label" htmlFor="edit-role">
                        Role
                        <select
                            id="edit-role"
                            name="roleId"
                            defaultValue={selectedUser.roleId}
                            className="user-edit-input"
                            required
                        >
                            {roleSelectList.map(role => (
                                <option key={role.id} value={role.id}>
                                    {role.roleName}
                                </option>
                            ))}
                        </select>
                    </label>

                    <label className="user-edit-label" htmlFor="edit-name">
                        Name
                        <input
                            id="edit-name"
                            name="name"
                            type="text"
                            defaultValue={selectedUser.name}
                            className="user-edit-input"
                            required
                        />
                    </label>

                    <div className="user-edit-actions">
                        <button
                            type="button"
                            onClick={closeModal}
                            className="user-cancel-button"
                            disabled={isSaving}
                        >
                            Cancel
                        </button>

                        <button
                            type="submit"
                            className="user-submit-button"
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
                <div className="user-edit-actions">
                    <button
                        type="button"
                        className="user-upload-button"
                        disabled={isSaving}
                        onClick={() => handleUpload()}
                    >
                        {isSaving ? "Uploading..." : "Upload"}
                    </button>
                    <button
                        type="button"
                        className="user-upload-button"
                        disabled={isSaving}
                        onClick={() => handleDownload()}
                    >
                        {isSaving ? "Downloading..." : "Download template"}
                    </button>
                </div>
            </BaseModal>

            <BaseModal
                isOpen={addModalOpen}
                title="Add User"
                onClose={closeAddModal}
            >
                <form onSubmit={submitAdd} className="user-edit-card">
                    <div className="user-edit-heading">
                        <button
                            type="button"
                            className="user-list-button"
                            onClick={() => openImportModal()}
                        >
                            Import Batch
                        </button>
                    </div>

                    <label className="user-edit-label" htmlFor="add-email">
                        Email
                        <input
                            id="add-email"
                            name="email"
                            type="email"
                            className="user-edit-input"
                            required
                        />
                    </label>

                    <label className="user-edit-label" htmlFor="new-password">
                        New Password
                        <input
                            id="new-password"
                            name="password"
                            type="password"
                            className="user-edit-input"
                            autoComplete="new-password"
                            minLength={6}
                            required
                        />
                    </label>

                    <label className="user-edit-label" htmlFor="add-role">
                        Role
                        <select
                            id="add-role"
                            name="roleId"
                            className="user-edit-input"
                            required
                        >
                            {roleSelectList.map(role => (
                                <option key={role.id} value={role.id}>
                                    {role.roleName}
                                </option>
                            ))}
                        </select>
                    </label>

                    <label className="user-edit-label" htmlFor="add-name">
                        Name
                        <input
                            id="add-name"
                            name="name"
                            type="text"
                            className="user-edit-input"
                            required
                        />
                    </label>

                    <div className="user-edit-actions">
                        <button
                            type="button"
                            onClick={closeAddModal}
                            className="user-cancel-button"
                            disabled={isSaving}
                        >
                            Cancel
                        </button>

                        <button
                            type="submit"
                            className="user-submit-button"
                            disabled={isSaving}
                        >
                            {isSaving ? "Saving..." : "Save"}
                        </button>
                    </div>
                </form>
            </BaseModal>
        </div>
    );
};

export default UserManage;
