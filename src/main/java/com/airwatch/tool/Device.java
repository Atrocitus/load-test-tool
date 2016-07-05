package com.airwatch.tool;

/**
 * Created by manishk on 7/5/16.
 */
public class Device {

    private String easDeviceId;
    private String easDeviceType;
    private String userId;

    public String getEasDeviceId() {
        return easDeviceId;
    }

    public Device setEasDeviceId(String easDeviceId) {
        this.easDeviceId = easDeviceId;
        return this;
    }

    public String getEasDeviceType() {
        return easDeviceType;
    }

    public Device setEasDeviceType(String easDeviceType) {
        this.easDeviceType = easDeviceType;
        return this;
    }

    public String getUserId() {
        return userId;
    }

    public Device setUserId(String userId) {
        this.userId = userId;
        return this;
    }
}
