package org.rohlik.damage_bot.models;

import java.sql.Time;

public class Bag {

    private String number;
    private String status;
    private String error;
    private byte[] image;
    private String state;

    private Time time;

    public Bag() {
        this.state = "INIT";
    }

    public Bag(String number) {
        this.number = number;
        this.state = "INIT";
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public byte[] getImage() {
        return image;
    }

    public void setImage(byte[] image) {
        this.image = image;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Time getTime() {
        return time;
    }

    public void setTime(Time time) {
        this.time = time;
    }
}


