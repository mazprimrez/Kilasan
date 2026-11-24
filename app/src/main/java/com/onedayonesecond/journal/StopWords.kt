package com.onedayonesecond.journal

/**
 * Words too common to say anything about what was written, in English and in
 * Indonesian (with the everyday spellings people type: "yg", "gak", "udah"). Patterns
 * leaves them out of "Words you used most".
 */
object StopWords {

    val ENGLISH: Set<String> = setOf(
        "first", "last", "next", "really", "thing", "things", "today", "time",
        "a", "about", "above", "after", "again", "against", "all", "also", "am", "an", "and", "any", "are",
        "as", "at", "be", "because", "been", "before", "being", "below", "between", "bit", "both", "but",
        "by", "can", "can't", "cant", "could", "couldn't", "couldnt", "day", "days", "did", "didn't",
        "didnt", "do", "does", "doesn't", "doesnt", "doing", "don't", "done", "dont", "down", "during",
        "each", "even", "ever", "every", "few", "for", "from", "further", "get", "gets", "getting", "gonna",
        "got", "gotta", "had", "has", "have", "having", "he", "her", "here", "hers", "herself", "him",
        "himself", "his", "how", "however", "i", "i'd", "i'll", "i'm", "i've", "if", "im", "in", "into",
        "is", "isn't", "isnt", "it", "it's", "its", "itself", "ive", "just", "kinda", "let", "lets", "like",
        "lot", "lots", "made", "make", "many", "may", "me", "might", "more", "most", "much", "must", "my",
        "myself", "no", "nor", "not", "now", "of", "off", "often", "on", "once", "one", "only", "or",
        "other", "our", "ours", "ourselves", "out", "over", "own", "really", "said", "same", "say", "says",
        "see", "she", "should", "shouldn't", "shouldnt", "so", "some", "still", "such", "than", "that",
        "that's", "the", "their", "theirs", "them", "themselves", "then", "there", "these", "they", "thing",
        "things", "this", "those", "though", "through", "time", "to", "today", "too", "took", "under",
        "until", "up", "upon", "us", "very", "wanna", "was", "wasn't", "wasnt", "way", "we", "well", "went",
        "were", "what", "when", "where", "which", "while", "who", "whom", "why", "will", "with", "won't",
        "wont", "would", "wouldn't", "wouldnt", "yeah", "yes", "yet", "you", "your", "yours", "yourself",
        "yourselves",
    )

    val INDONESIAN: Set<String> = setOf(
        // Filler that names no thing: "jam 6", "akhirnya", "di depan".
        "jam", "akhirnya", "bilang", "lama", "depan", "belakang", "kemarin", "besok", "nanti", "lagi", "hari", "kali", "orang",
        "ada", "adalah", "agak", "agar", "aj", "aja", "ajah", "akan", "aku", "amat", "anda", "apa", "apakah",
        "atas", "atau", "bagi", "bahkan", "bahwa", "banget", "baru", "bawah", "beberapa", "begitu", "belum",
        "benar", "bener", "berapa", "bgt", "biar", "bisa", "blm", "buat", "bukan", "cuma", "dalam", "dan",
        "dapat", "dari", "deh", "dengan", "dgn", "di", "dia", "diri", "dong", "dr", "dulu", "emang", "engga",
        "enggak", "entah", "ga", "gak", "gini", "gitu", "gk", "gua", "gue", "guys", "gw", "haha", "hal",
        "hampir", "hanya", "hari", "harus", "hehe", "ia", "ini", "itu", "iya", "jadi", "jangan", "jd", "jg",
        "juga", "kak", "kalau", "kali", "kalo", "kamu", "kan", "karena", "kayak", "kayaknya", "ke",
        "kemudian", "kenapa", "kepada", "ketika", "kita", "klo", "kok", "krn", "kurang", "lagi", "lah",
        "lain", "lalu", "lebih", "lg", "lo", "loh", "lu", "mah", "maka", "mana", "masih", "mau", "me",
        "memang", "mereka", "mungkin", "nah", "namun", "nanti", "ngga", "nggak", "nya", "oleh", "pada",
        "padahal", "paling", "pas", "pun", "saat", "saja", "sama", "sambil", "sampai", "sangat", "saya",
        "sdh", "sebelum", "sedang", "sedikit", "sehingga", "sejak", "sekali", "sekarang", "seperti",
        "setelah", "siapa", "sih", "sini", "situ", "sm", "sudah", "supaya", "tadi", "tahu", "tak", "tapi",
        "tau", "telah", "tentang", "terus", "tetap", "tiba", "toh", "tp", "trs", "tuh", "udah", "udh",
        "untuk", "utk", "walau", "wkwk", "wkwkwk", "ya", "yaa", "yang", "yg",
    )

    val ALL: Set<String> = ENGLISH + INDONESIAN
}
