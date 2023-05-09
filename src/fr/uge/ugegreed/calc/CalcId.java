package fr.uge.ugegreed.calc;

import java.net.InetSocketAddress;

/**
 * 
 * @param id {@link Integer} id du calcul
 * @param origin {@link InetSocketAddress} du noeud qui est à l'origine de ce calcul
 */
public record CalcId(int id, InetSocketAddress origin) {}
