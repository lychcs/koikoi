package com.lychcs.koikoi.model.fuku;

/**
 * Erlaubt den Fuku-Consumables, Werte des Spiels/Runs zu verändern,
 * ohne direkt mit dem GameScreen gekoppelt zu sein.
 */
public interface FukuContext {
    int getMon();               // Gibt aktuelle Münzen zurück
    void addMon(int amount);    // Fügt Münzen hinzu (oder zieht sie ab bei negativen Werten)
    void addMaxHands(int amount); // Gibt dauerhaft mehr Hände für den Run
    void addMaxDiscards(int amount);
    void addMaxInterestCap(int amount);}
