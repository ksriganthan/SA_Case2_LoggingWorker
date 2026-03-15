package com.fhnw.sa_case2_loggingworker.DTO;

public class Decision {
   private Integer ruleId;
   private String benutzerId;
   private String grund;

   private String bestellnummer;
   private String lieferadresse;
   private String spediteur;
   private String versandart;
   private String entscheidungsart;
   private String land;
   private Long gewicht;

   public Decision(Integer ruleId, String benutzerId, String grund, String bestellnummer, String lieferadresse, String spediteur, String versandart, String entscheidungsart, String land, Long gewicht) {
       this.ruleId = ruleId;
       this.benutzerId = benutzerId;
       this.grund = grund;
       this.bestellnummer = bestellnummer;
       this.lieferadresse = lieferadresse;
       this.spediteur = spediteur;
       this.versandart = versandart;
       this.entscheidungsart = entscheidungsart;
       this.land = land;
       this.gewicht = gewicht;
   }

   public Decision() {

   }

    public Integer getRuleId() {
        return ruleId;
    }

    public void setRuleId(Integer ruleId) {
        this.ruleId = ruleId;
    }

    public String getBenutzerId() {
        return benutzerId;
    }

    public void setBenutzerId(String benutzerId) {
        this.benutzerId = benutzerId;
    }

    public String getGrund() {
        return grund;
    }

    public void setGrund(String grund) {
        this.grund = grund;
    }

    public String getBestellnummer() {
        return bestellnummer;
    }

    public void setBestellnummer(String bestellnummer) {
        this.bestellnummer = bestellnummer;
    }

    public String getLieferadresse() {
        return lieferadresse;
    }

    public void setLieferadresse(String lieferadresse) {
        this.lieferadresse = lieferadresse;
    }

    public String getSpediteur() {
        return spediteur;
    }

    public void setSpediteur(String spediteur) {
        this.spediteur = spediteur;
    }

    public String getVersandart() {
        return versandart;
    }

    public void setVersandart(String versandart) {
        this.versandart = versandart;
    }

    public String getEntscheidungsart() {
        return entscheidungsart;
    }

    public void setEntscheidungsart(String entscheidungsart) {
        this.entscheidungsart = entscheidungsart;
    }

    public String getLand() {
        return land;
    }

    public void setLand(String land) {
        this.land = land;
    }

    public Long getGewicht() {
        return gewicht;
    }

    public void setGewicht(Long gewicht) {
        this.gewicht = gewicht;
    }
}

