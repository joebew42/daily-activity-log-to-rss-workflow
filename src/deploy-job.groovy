node('', {

    def url = new URL('http://www.google.com')
    def httpCon = (HttpURLConnection) url.openConnection();

    httpCon.setRequestMethod('POST')

})

