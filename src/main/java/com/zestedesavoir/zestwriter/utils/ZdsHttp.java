package com.zestedesavoir.zestwriter.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpCookie;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.*;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zestedesavoir.zestwriter.model.MetadataContent;

import javafx.util.Pair;

import javax.net.ssl.SSLContext;


public class ZdsHttp {
    private String idUser;
    private String galleryId;
    private String login;
    private String password;
    private String hostname;
    private String port;
    private String protocol;
    private boolean authenticated = false;
    private List<MetadataContent> contentListOnline;
    private HttpClient client;
    private CookieStore cookieStore;
    private String cookies;
    private HttpClientContext context;
    private String localSlug;
    private final Logger logger;
    private static String USER_AGENT = "Mozilla/5.0";
    private Configuration config;
    private final static Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private final static Pattern WHITESPACE = Pattern.compile("[\\s]");

    public String getLogin() {
        return login;
    }


    public String getLocalSlug() {
        return localSlug;
    }


    public String getGalleryId() {
        return galleryId;
    }

    public void setGalleryId(String galleryId) {
        this.galleryId = galleryId;
    }


    public void setLocalSlug(String localSlug) {
        this.localSlug = localSlug;
    }

    public List<MetadataContent> getContentListOnline() {
        return contentListOnline;
    }

    private String getBaseUrl() {
        if (this.port.equals("80")) {
            return this.protocol + "://" + this.hostname;
        } else {
            return this.protocol + "://" + this.hostname + ":" + this.port;
        }
    }

    private String getLoginUrl() {
        return getBaseUrl() + "/membres/connexion/?next=/";
    }

    private String getPersonalTutorialUrl() {
        return getBaseUrl() + "/contenus/tutoriels/" + idUser + "/";
    }

    private String getPersonalArticleUrl() {
        return getBaseUrl() + "/contenus/articles/" + idUser + "/";
    }

    private String getDownloadDraftContentUrl(String id, String slug) {
        return getBaseUrl() + "/contenus/telecharger/" + id + "/" + slug + "/";
    }

    private String getDraftContentUrl(String id, String slug) {
        return getBaseUrl() + "/contenus/" + id + "/" + slug + "/";
    }

    private String getImportImageUrl() {
        return getBaseUrl() + "/galerie/image/ajouter/" + getGalleryId()+ "/";
    }

    private String getImportContenttUrl(String idContent, String slugContent) {
        return getBaseUrl() + "/contenus/importer/" + idContent + "/" + slugContent + "/";
    }

    private String getImportNewContenttUrl() {
        return getBaseUrl() + "/contenus/importer/archive/nouveau/";
    }

    public String getOnlineContentPathDir() {
        return config.getOnlineSaver().getBaseDirectory();
    }

    public String getOfflineContentPathDir() {
        return config.getOfflineSaver().getBaseDirectory();
    }

    public static String toSlug(String input) {
        String nowhitespace = WHITESPACE.matcher(input).replaceAll("-");
        String normalized = Normalizer.normalize(nowhitespace, Form.NFD);
        String slug = NONLATIN.matcher(normalized).replaceAll("");
        return slug.toLowerCase(Locale.ENGLISH);
    }


    private void initContext() {
        context = HttpClientContext.create();
        cookieStore = new BasicCookieStore();
        context.setCookieStore(cookieStore);

        try {
            SSLContextBuilder builder = new SSLContextBuilder();
            builder.loadTrustMaterial(null, new TrustStrategy() {
                @Override
                public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    return true;
                }
            });

            SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build());

            Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory> create().register("https", sslsf).register("http", new PlainConnectionSocketFactory()).build();
            PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
            // Increase max total connection to 200
            cm.setMaxTotal(500);
            // Increase default max connection per route to 20
            cm.setDefaultMaxPerRoute(20);

            client = HttpClients.custom()
                    .setRedirectStrategy(new LaxRedirectStrategy())
                    .setConnectionManager(cm)
                    .setSSLSocketFactory(sslsf)
                    .build();

        } catch (NoSuchAlgorithmException e) {
            logger.error(e.getMessage(), e);
        } catch (KeyManagementException e) {
            logger.error(e.getMessage(), e);
        } catch (KeyStoreException e) {
            logger.error(e.getMessage(), e);
        }
        
        contentListOnline = new ArrayList<>();

    }

    public ZdsHttp(Configuration config) {
        super();
        logger = LoggerFactory.getLogger(ZdsHttp.class);
        this.config = config;
        this.protocol = config.getAdvancedServerProtocol();
        this.hostname = config.getAdvancedServerHost();
        this.port = config.getAdvancedServerPort();

        initContext();
    }

    public void authToGoogle(List<HttpCookie> cookies, String login, String id) {
        if(login != null && id != null) {
            this.login = login;
            this.idUser = id;
            logger.info("L'identifiant de l'utilisateur " + this.login + " est : " + idUser);
            cookieStore = new BasicCookieStore();
            for(HttpCookie cookie:cookies) {
                BasicClientCookie c = new BasicClientCookie(cookie.getName(), cookie.getValue());
                c.setDomain(cookie.getDomain());
                c.setPath(cookie.getPath());
                c.setSecure(cookie.getSecure());
                c.setVersion(cookie.getVersion());
                c.setComment(cookie.getComment());
                cookieStore.addCookie(c);
            }
            context.setCookieStore(cookieStore);
            this.authenticated = true;
        }
        else {
            logger.debug("Le login de l'utilisateur n'a pas pu être trouvé");
        }
    }


    private String getCookieValue(CookieStore cookieStore, String cookieName) {
        String value = null;
        for (Cookie cookie : cookieStore.getCookies()) {

            if (cookie.getName().equals(cookieName)) {
                value = cookie.getValue();
            }
        }
        return value;
    }

    private Pair<Integer, String> sendPost(String url, HttpEntity entity) {
        HttpPost post = new HttpPost(url);
        try {
            // add header
            post.setHeader("Host", this.hostname);
            post.setHeader("User-Agent", USER_AGENT);
            post.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            post.setHeader("Accept-Language", "fr-FR");
            post.setHeader("Cookie", this.cookies);
            post.setHeader("Connection", "keep-alive");
            post.setHeader("Referer", url);

            post.setEntity(entity);
            HttpResponse response = client.execute(post, context);

            int responseCode = response.getStatusLine().getStatusCode();
            BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
            return new Pair<>(responseCode, rd.lines().collect(Collectors.joining("\n")));
        } catch (IOException e) {
            logger.error("Impossible d'executer la requête POST", e);
        }

        return new Pair<>(500, null);
    }

    public void initGalleryId(String idContent, String slugContent) throws IOException {
        String url = getDraftContentUrl(idContent, slugContent);
        logger.info("Tentative de récupération de la page offline du contenu "+idContent+"("+slugContent+")");

        HttpGet get = new HttpGet(url);
        HttpResponse response = client.execute(get, context);
        this.cookies = response.getFirstHeader("Set-Cookie").toString();
        logger.info("Tentative réussie");

        BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));

        Document doc = Jsoup.parse(rd.lines().collect(Collectors.joining("\n")));

        Elements links = doc.select("a[href^=/galerie/]");
        for (Element link : links) {
            String ref = link.attr("href").trim();
            String[] tab = ref.split("/");
            if(tab.length >= 4) {
                this.galleryId = tab[2];
            }
        }
    }

    public String getId(String homeConnectedContent) {
        Document doc = Jsoup.parse(homeConnectedContent);
        Elements sections = doc.select("div.my-account-dropdown > ul > li > a[href^=/contenus/tutoriels]");
        for (Element section : sections) {
            String ref = section.attr("href").trim();
            if(ref.startsWith("/contenus/tutoriels")) {
                String[] splt = ref.split("/");
                if(splt.length >= 4) {
                    return splt[3];
                }
                else {
                    return null;
                }
            }
        }
        return null;
    }

    public boolean login(String login, String password) throws IOException {
        this.login = login;
        this.password = password;

        HttpGet get = new HttpGet(getLoginUrl());
        HttpResponse response = client.execute(get, context);
        this.cookies = response.getFirstHeader("Set-Cookie").getValue();

        List<NameValuePair> urlParameters = new ArrayList<>();
        urlParameters.add(new BasicNameValuePair("username", this.login));
        urlParameters.add(new BasicNameValuePair("password", this.password));
        urlParameters.add(new BasicNameValuePair("csrfmiddlewaretoken", getCookieValue(cookieStore, "csrftoken")));

        Pair<Integer, String> pair = sendPost(getLoginUrl(), new UrlEncodedFormEntity(urlParameters));
        if (pair.getKey() == 200 && pair.getValue().contains("my-account-dropdown")) {
            this.authenticated = true;
            this.idUser = getId(pair.getValue());
            logger.info("Utilisateur " + this.login + " connecté");
        } else {
            logger.debug("Utilisateur " + this.login + " non connecté via " + getLoginUrl());
        }
        return this.authenticated;
    }

    public void logout() {
        this.login = null;
        this.password = null;
        this.idUser = null;
        this.galleryId = null;
        this.cookieStore = null;
        this.authenticated = false;
    }

    public String importImage(File file) throws IOException {
        if(getGalleryId() != null) {
            String url = getImportImageUrl();
            HttpGet get = new HttpGet(url);
            HttpResponse response = client.execute(get, context);
            this.cookies = response.getFirstHeader("Set-Cookie").getValue();

            // load file in form
            FileBody cbFile = new FileBody(file);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
            builder.addPart("physical", cbFile);
            builder.addPart("title", new StringBody("Image importée via ZestWriter", Charset.forName("UTF-8")));
            builder.addPart("csrfmiddlewaretoken", new StringBody(getCookieValue(cookieStore, "csrftoken"), ContentType.MULTIPART_FORM_DATA));

            Pair<Integer, String> resultPost = sendPost(url, builder.build());

            Document doc = Jsoup.parse(resultPost.getValue());
            Elements endPoints = doc.select("input[name=avatar_url]");
            for(Element point:endPoints) {
                return getBaseUrl() + point.attr("value").trim();
            }
        }
        return "http://";
    }

    private boolean uploadContent(String filePath, String url, String msg) throws IOException{
        HttpGet get = new HttpGet(url);
        HttpResponse response = client.execute(get, context);
        this.cookies = response.getFirstHeader("Set-Cookie").getValue();

        // load file in form
        FileBody cbFile = new FileBody(new File(filePath));
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        builder.addPart("archive", cbFile);
        builder.addPart("subcategory", new StringBody("115", ContentType.MULTIPART_FORM_DATA));
        builder.addPart("msg_commit", new StringBody(msg, Charset.forName("UTF-8")));
        builder.addPart("csrfmiddlewaretoken", new StringBody(getCookieValue(cookieStore, "csrftoken"), ContentType.MULTIPART_FORM_DATA));

        Pair<Integer, String> resultPost = sendPost(url, builder.build());
        int statusCode = resultPost.getKey();

        switch (statusCode) {
            case 404:
                logger.debug("Your target id and slug is incorrect, please give us real informations");
            case 403:
                logger.debug("Your are not authorize to do this task. Please check if your are login");
        }

        return statusCode == 200 && (! resultPost.getValue ().contains ("alert-box alert"));
    }
    public boolean importNewContent(String filePath, String msg) throws IOException {

        logger.debug("Tentative d'import via l'url : " + getImportNewContenttUrl());
        return uploadContent(filePath, getImportNewContenttUrl(), msg);
    }

    public boolean importContent(String filePath, String targetId, String targetSlug, String msg)
            throws IOException {
        logger.debug("Tentative d'import via l'url : " + getImportContenttUrl(targetId, targetSlug));
        return uploadContent(filePath, getImportContenttUrl(targetId, targetSlug), msg);
    }

    public void initInfoOnlineContent(String type) throws IOException {
        HttpGet get = null;

        logger.info("Initialisation des metadonnées contenus en ligne de type " + type);

        if (type.equals("tutorial")) {
            logger.info("Tentative de joindre l'url : " + getPersonalTutorialUrl());
            get = new HttpGet(getPersonalTutorialUrl());
        } else {
            if (type.equals("article")) {
                logger.info("Tentative de joindre l'url : " + getPersonalArticleUrl());
                get = new HttpGet(getPersonalArticleUrl());
            }
        }

        HttpResponse response = client.execute(get, context);
        this.cookies = response.getFirstHeader("Set-Cookie").toString();
        logger.info("Tentative réussie");

        BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));

        Document doc = Jsoup.parse(rd.lines().collect(Collectors.joining("\n")));
        Elements sections = doc.select("article > a[href^=/contenus/]");
        for (Element section : sections) {
            String ref = section.attr("href").trim();
            logger.trace("Chaine à decrypter pour trouver le slug : " + ref);
            if (ref.startsWith("/contenus/")) {
                String[] tab = ref.split("/");
                MetadataContent onlineContent = new MetadataContent(tab[2], tab[3], type);
                if(!getContentListOnline().contains(onlineContent)) {
                    getContentListOnline().add(onlineContent);
                }
            }
        }
        logger.info("Contenu de type " + type + " chargés en mémoire : " + getContentListOnline());
    }

    private String getTargetSlug(String targetId, String type) {
        for (MetadataContent metadata : contentListOnline) {
            if (metadata.getId().equals(targetId) && metadata.getType().equalsIgnoreCase(type)) {
                return metadata.getSlug();
            }
        }
        return null;
    }

    public void downloaDraft(String targetId, String type) throws IOException {
        String targetSlug = getTargetSlug(targetId, type);
        HttpGet get = new HttpGet(getDownloadDraftContentUrl(targetId, targetSlug));
        logger.debug("Tentative de téléchargement via le lien : " + getDownloadDraftContentUrl(targetId, targetSlug));


        HttpResponse response = client.execute(get, context);

        InputStream is = response.getEntity().getContent();
        String filePath = getOnlineContentPathDir() + File.separator + targetSlug + ".zip";
        FileOutputStream fos = new FileOutputStream(new File(filePath));

        int inByte;
        while ((inByte = is.read()) != -1)
            fos.write(inByte);
        is.close();
        fos.close();
    }

    public void unzipOnlineContent(String zipFilePath) {

        byte[] buffer = new byte[1024];
        try {
            String dirname = Paths.get(zipFilePath).getFileName().toString();
            dirname = dirname.substring(0, dirname.length() - 4);
            // create output directory is not exists
            File folder = new File(getOfflineContentPathDir() + File.separator + dirname);
            logger.debug("Tentative de dezippage de " + zipFilePath + " dans " + folder.getAbsolutePath());
            if (!folder.exists()) {
                folder.mkdir();
                // get the zip file content
                ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath));
                // get the zipped file list entry
                ZipEntry ze = zis.getNextEntry();

                while (ze != null) {

                    String fileName = ze.getName();
                    logger.trace("Traitement du fichier : " + fileName);
                    File newFile = new File(folder + File.separator + fileName);
                    new File(newFile.getParent()).mkdirs();

                    FileOutputStream fos = new FileOutputStream(newFile);

                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }

                    fos.close();
                    ze = zis.getNextEntry();
                }

                zis.closeEntry();
                zis.close();
                logger.info("Dézippage dans " + folder.getAbsolutePath() + " réalisé avec succès");
            } else {
                logger.debug("Le répertoire dans lequel vous souhaitez dezipper existe déjà ");
            }

        } catch (IOException ex) {
            logger.debug("Echec de dezippage dans " + zipFilePath);
        }
    }

    public boolean isAuthenticated() {
        return authenticated;
    }
}
