import request from "@/config/request/axios.js";

export const getMessageList =
    () =>
        request.get("/general/message/getMessageList");

export const readMessages =
    ids =>
        request.post("/general/message/readMessages", null, {
            params: { ids },
            paramsSerializer: {
                indexes: null
            }
        });