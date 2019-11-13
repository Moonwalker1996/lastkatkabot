package com.senderman.anitrackerbot;

import org.jsoup.Connection;
import org.jsoup.Jsoup;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class AnimeDownloaders {

    private static Map<String, String> anidubCookies = null;

    static File getAnidubTorrent(String url) throws Exception {

        // login to anidub
        var conn = Jsoup.connect(url).method(Connection.Method.POST);
        if (anidubCookies != null) {
            conn.cookies(anidubCookies);
        } else {
            var account = Services.config().getAnidata();
            var username = account.split(":")[0];
            var password = account.split(":")[1];
            conn.data("login_name", username, "login_password", password, "login", "submit");
        }
        var resp = conn.execute();
        if (anidubCookies == null)
            anidubCookies = resp.cookies();

        // download torrent after logging in
        var page = resp.parse();
        var torrentDiv = Jsoup.parse(page.toString()).selectFirst("div.torrent_H");
        var torrentLink = torrentDiv.selectFirst("a").attr("href");
        torrentLink = "https://tr.anidub.com" + torrentLink;
        var torrent = Jsoup.connect(torrentLink)
                .method(Connection.Method.GET)
                .cookies(anidubCookies)
                .ignoreContentType(true)
                .execute()
                .bodyStream();

        byte[] buffer = new byte[4096];
        var file = new File("anime.torrent");
        var fos = new FileOutputStream(file);
        int length;
        while ((length = torrent.read(buffer)) != -1) {
            fos.write(buffer, 0, length);
        }
        torrent.close();
        fos.close();
        return file;

    }

    static File getAnistarTorrent(String url) throws Exception {
        var doc = Jsoup.parse(new URL(url), 10000);
        var torrentList = doc.selectFirst("div.list_torrent").select("div.torrent");

        // download all torrents to zip
        var zipFile = new File("anime.zip");
        var zout = new ZipOutputStream(new FileOutputStream(zipFile));
        for (var torrent : torrentList) {
            var name = torrent.selectFirst("div.info_d1").text() + ".torrent";
            var in = new URL("https://anistar.org" + torrent.selectFirst("a").attr("href")).openStream();
            int length;
            byte[] buffer = new byte[4096];

            zout.putNextEntry(new ZipEntry(name));
            while ((length = in.read(buffer)) != -1) {
                zout.write(buffer, 0, length);
            }
            in.close();
            zout.closeEntry();
        }
        zout.close();
        return zipFile;
    }

    static File getNyaasiTorrent(String url) throws Exception {
        var doc = Jsoup.parse(new URL(url), 10000);
        var torrentlink = "https://nyaa.si" + doc.selectFirst("div.panel-footer").selectFirst("a").attr("href");
        var u = new URL(torrentlink);
        var in = u.openStream();
        var file = new File("anime.torrent");
        var fos = new FileOutputStream(file);
        int length;
        byte[] buffer = new byte[4096];
        while ((length = in.read(buffer)) != -1) {
            fos.write(buffer, 0, length);
        }
        in.close();
        fos.close();
        return file;
    }

}
