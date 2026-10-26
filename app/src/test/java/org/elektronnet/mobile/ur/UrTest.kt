package org.elektronnet.mobile.ur

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// the vectors were produced by the reference implementation @ngraveio/bc-ur 1.1.12 (MIT) with the type crypto-psbt
class UrTest {
    private class Vector(val name: String, val hex: String, val maxFragment: Int, val parts: List<String>)

    private fun bytes(hex: String) = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private val vectors = listOf(
        Vector("tiny", "3c5e81b40c5ec68e04a3406c97d63cfbdc53ae88", 100, listOf(
            "ur:crypto-psbt/ghfnhylyqzbnhyswmnaaotfzjzmstbfnzouogupllodaclislt",
            "ur:crypto-psbt/ghfnhylyqzbnhyswmnaaotfzjzmstbfnzouogupllodaclislt",
            "ur:crypto-psbt/ghfnhylyqzbnhyswmnaaotfzjzmstbfnzouogupllodaclislt",
        )),
        Vector("psbtish", "3de99ced0cdb5c0d11fbf988738c9ac7c76b79da5c7b17bfd55b2dfb1f74741b1d9fc5159bed44975a5083b64f28dfa061ff7c2d40eb967e2c6f2d00b1c2258bbde25f68a3d4807bc9f553e23546f3b7350d4bd3c23582c16a545a34de34adb370448656196277c0dcc63baf9513bed290da0ebf6ffee93248d9cffcf307e23dfb3decd5a58f5f2f060222c3165d7dfe308d8168890ead3c712ebfbf6e1de9159627d85fa3764457ae4d03c98f99c3884934f4ca0753840f86e86deb01fd3e6eef3f22f12354088a2eaeec6f0fde740483be4a6591dcf99f1b0232f095cfb1bb28a8390de88e5eded3900168d4e9c949f900fa3c87df6aa4bad97b43426163b6d7681eb96aa9ce2ce2c3766b56184f723ab111d6f9b5089bdd2cc95b5a26ce5d036a677dd650b4138f799931", 100, listOf(
            "ur:crypto-psbt/1-4/lpadaacfaddlcylymonyidhdgshkaddwfswlnswebnuyhhbtbyzoytlojklknyststjekktnhhkgchrstlhpdpzoctjyjycwcaneskbzndwefymshtgdlsrpgwdeurnbhszmkedpfzwmmtkbdwjldpaepasadaluryvoheisottylakgsodmlposlr",
            "ur:crypto-psbt/2-4/lpaoaacfaddlcylymonyidhdgsykguvoecfgwfrlecbtgrtesaeclfseimghhteeueeepmqdjofylnhfcfidktrtuoswfrpemdbwrntdmhtnbarsjlzewleyfdtatkztwfatvofszofswptlonmyhedlamaocpsrcmhlkizedylglyisldeeldjord",
            "ur:crypto-psbt/3-4/lpaxaacfaddlcylymonyidhdgsbapmfnjsdmrsrsjtcawlbzmtditpheotkofyhgplgtaxsomynlsrlogaeewksgatgulrbslnvsjnwmadzcfmjtwsfhcpwncnghayledmplwpjlbsuejyaalsrngeihmeuoytnecwaoeywtmdtkparkdeseahzeyk",
            "ur:crypto-psbt/4-4/lpaaaacfaddlcylymonyidhdgspdesbtvsmnhyuetemhadistywlsogaytaezsfnlturimoxrdtakgfxfwhsiarptsisckrhimpttodwvosrkojehfcsgwjpftpabytbytreayndutdwsohphtdstohlaximiokitbgdqzbwmykknlehaebbmefsva",
            "ur:crypto-psbt/5-4/lpahaacfaddlcylymonyidhdgspdesbtvsmnhyuetemhadistywlsogaytaezsfnlturimoxrdtakgfxfwhsiarptsisckrhimpttodwvosrkojehfcsgwjpftpabytbytreayndutdwsohphtdstohlaximiokitbgdqzbwmykknlehaefxghkpzm",
            "ur:crypto-psbt/6-4/lpamaacfaddlcylymonyidhdgszozeuefyisgsayhpbeoeswghbghtnnsocpckiajokkplknzmutfeuegdhflsbkuymdrsnbbwzoteesmedidyttlasesbsrjelgstkoutptbagmwkvlmkttdsehbzgemsueuyhhbthegwbaonfwdyteoyurhsadla",
            "ur:crypto-psbt/7-4/lpataacfaddlcylymonyidhdgsbapmfnjsdmrsrsjtcawlbzmtditpheotkofyhgplgtaxsomynlsrlogaeewksgatgulrbslnvsjnwmadzcfmjtwsfhcpwncnghayledmplwpjlbsuejyaalsrngeihmeuoytnecwaoeywtmdtkparkdefehstpti",
            "ur:crypto-psbt/8-4/lpayaacfaddlcylymonyidhdgsgusttepsvabgtblolaotpllazomutsdycpveheylolssuefeaafmntbgemvtrfbnzcoycfkkgmcabzjkvefgrdtbtalrpagyfntbnbdkceamsodttkgylekechuychmwqzrfcluybszocadrfrptvooyvyahmsnn",
            "ur:crypto-psbt/9-4/lpasaacfaddlcylymonyidhdgsbkswzmmebsmnfrlrhpzmotmeaeimhefxplkbmkdysnryaacflbdtcpstjzsnflbwldtlaoiesntpaevsasaodplkldatatckbbasaefevlknveindkstwkgdksynchdakonlpkiyweoxkpldwsdtnlisnlfgfpuy",
            "ur:crypto-psbt/10-4/lpbkaacfaddlcylymonyidhdgsolmwehnlnbvyhsrylgvskifwtobycmhtkornjedtmoinjnecfzrosbbdgomsketifrnyrpwpfpotstvlfmfdahrhdijnlscfvwcfhhtscwvewktdwzryhetamklretmorpnnvosngmlnvlcyrpdeledelsgemtvw",
            "ur:crypto-psbt/11-4/lpbdaacfaddlcylymonyidhdgspdesbtvsmnhyuetemhadistywlsogaytaezsfnlturimoxrdtakgfxfwhsiarptsisckrhimpttodwvosrkojehfcsgwjpftpabytbytreayndutdwsohphtdstohlaximiokitbgdqzbwmykknlehaerkaxldvl",
            "ur:crypto-psbt/12-4/lpbnaacfaddlcylymonyidhdgsgusttepsvabgtblolaotpllazomutsdycpveheylolssuefeaafmntbgemvtrfbnzcoycfkkgmcabzjkvefgrdtbtalrpagyfntbnbdkceamsodttkgylekechuychmwqzrfcluybszocadrfrptvooyihhspark",
            "ur:crypto-psbt/13-4/lpbtaacfaddlcylymonyidhdgspdesbtvsmnhyuetemhadistywlsogaytaezsfnlturimoxrdtakgfxfwhsiarptsisckrhimpttodwvosrkojehfcsgwjpftpabytbytreayndutdwsohphtdstohlaximiokitbgdqzbwmykknlehaemhwefhwk",
            "ur:crypto-psbt/14-4/lpbaaacfaddlcylymonyidhdgsykguvoecfgwfrlecbtgrtesaeclfseimghhteeueeepmqdjofylnhfcfidktrtuoswfrpemdbwrntdmhtnbarsjlzewleyfdtatkztwfatvofszofswptlonmyhedlamaocpsrcmhlkizedylglyisldiaghcemw",
        )),
        Vector("large", "401693386006721edffeda7fa4dc981723f3ece38c2e8c42f1d227d4770f0589d4d8ae6195b2b6b7fd7319317220c497cd633745044755e2f431df95565021fa1aebce6eb54e1e821e3e9ab27d353ab1e1d1b2d66627208aa6fc6dbf6e0b13d3facc2b62487dd7e9325087436703a55d794197b38d9a3b616bef3cebf392ec06418a80f3fab5d04f90af0c5675c4f809934da5aa7741b88108d8bbe7bd89fffb9ec80d8b9d3fbf06f0725a939207689611231b41488f6ef9ab995cb347eae18ea7bf9647253b1a5770c7a4d34ab16d5abb85c0af45cdf5cbe1299684b2026f0dd33a62f9ab9a1e7f92ed2527d1f7c41e39c9dedfdb17aeee9f93e4c3c172c83c7f983d276c23cbaf3a3a17d0fa666f1f1ad74272975db84b3bf5c30cdc2f4e54ebcd760cc86fe40ab117bc4642dcb20fd22e3dbc2d63fbc27183b7320f82a9fe3c62e09545edf3a9a5ff5834c48c1713b5dea4c573c12024608245380a09c35a7871d4648bdf42992884347944fe6ac5fe8fd34764e496398b4ef95820b4ccfd8eaa2ccf665be3d9ae4b9c27260cbd32cc7aa5b51e0c8ebadb5562fe49ca36ed4d5149dfc94ca85e110be18674e764dc216b7d30e64cff56991a10cb20e2b9a5693e0e54c76e2b128e92570fdd10f9b7e2c64092208ea2b075339c95e7eb4f157adbe19e9955c6cfc7c05773b260592ddb7f5766598cf55d834b9f64802539a1fe2ab0e39c679b66c0893d95e901a51cba20b1ec3fd83ce83a21b9757325f82054bde9ff51de8c9de3f86b8a1c7341d610c7be17a4d67bd074878b3aecd554ddc1bc807d5cc8432bfd27439e8888d71e5525759180bc7c877264bd56bb7157986ce3eca187072bbc404930500f695430e2814eb4ed99cf73d7b3f8a3548c528462822a5fbe5174f240a19d53378fe9b8f6b347912a4cc4efaa82eb2dce0ad648947cb86312311360f888fb8e29ca0ad8b32ae3ec9a88724956f44936e524bb00d5489a0ab805be8ec46bbf1bb53e72241fe7253bc4d7b2c4aa40c933f9671c3cdff3576608fef3fb65ca614a4a611ca8248099ad52942495dab024cd0db558fe4c1cfc3d7f23f214023b5d9d00ff4bdf921e4a1f13f028e87da31adfcb4212bf9ef71708bf4ebd40236535cb903682bb197fcc27faeee5db8f8c6c7c7c983169394bbdf68b2e1fd7b5066cc0577b8ba251f5330c1e6845806ef1e2e81295e05b657484e9cd44a1240aee8c9a5893746eb46619c9b908f6dede6d469c1f688e6b4e62887592e79569d60220ab389b8d6ba04a9c102b506aef06af6745dc98efde059869e709400fd9353ab97c40016a5b57b05d43f592d7a1727916c424fef8707e2f4b3a894ee69ea2a3edc76089e673ff3a817bc0f937d710a12c2e78c6e65090d1d42289e4b6d3fe5e527a284a1d5da41cb284557e4867341381ccfa713623fabe3103a7a6e089909f87b9cfae703632221edda3f48c1c96cef51a73d6adff5acb10f9a31088c400af2bda2e7585907da776bbbefe4ab450e36ac94f1d5270355ea6d0626f957d5cebe369c2b3446f3a9ff004dc138ae1e27ac7bf9dc36e75f373aa0cede6b4a115c45725afceda4f058e455658811c4b2fcdf615119a9282e19a57e1716b550e00bd34ffb981b81a5a8eb201b068918a7f2be51603c6907d2ff76e393ec8ac9b57702b204adc94359d4dfa0787bb3f2e7cf688456fc180c3f14131b3c99bb010b789f0e270426672140d6221a44240ba37a6d556f457bc231fc568a0ab571e2821ca3bd4dd6a6c1de9a4b3bbf39b73a321c4c631d6abe3e097605f07a2407694fa06cab8e65f26f22b7a3bf58135795313f05af5fd31d9b21788e4a18c73fcf90c265c8786805923cb8ecca28153b92a48dc94f1f5b2c5c344444ddc70dcb7ee8427b76a5a810f559f71ba4b4b0f5b7a5f4e79abee4c11b4a7306fda763e7502f31aa2c1a46895a26349e02c595550895c2215dcbb706c98ac7ba871eb2fc4b34e23ea1f4a917b97b5cf9b3449e44891a5477670d4e2cbf6a3e8d62b401afa76f07b6049d5ab97351652cc07df51b9c106beaf8855bc63cac0d05c45093b2eb4946ed93d74b902f3eeb2dafd0205de3cae9ed57a", 120, listOf(
            "ur:crypto-psbt/1-13/lpadbtcfahurcybgbztejnhdjyhkahuofzcmmuethnamjpckurzetnlboxuomkchcnwfwpvllkdmlkfwwntddityktbsahldtytpplhsmdprrprlzcjkcfehjpcxssmssniaemfeaaflgovowkehurmdhfgdclzscywmtojtreglcklfckfmnyprkiecftpavyttprtbiydicxleolztjnrsjtbdbwtezssfdnidfdkitswleygdltfxioaxonhlkkmoihehhy",
            "ur:crypto-psbt/2-13/lpaobtcfahurcybgbztejnhdjyfpmsqdlgnyfrhsjewsfnwmwfmowpamfplelawfzsretigwmhpebnhfkpssyaasmugtonpkktfprolyaytprkvdryldzmzonnspbtluntfhrsamwtjphtmumoatismtbycncwfpfdmyjtytpynlhhqdflwdvymnosrsmtfldafrcyhgjostoxtegepajnhtrklprtpefesnyksbvydtmtlrpraojlbtteftidytpyhgadzths",
            "ur:crypto-psbt/3-13/lpaxbtcfahurcybgbztejnhdjynycklbmowedadittylssckessoueuruychplwynemuvesrsejpspfnlbmkfsdijzcnsbpeftftchtizsiyjlctcytsfwjpmshlrogrfryksrbnuodlglghwmsnkobnspjlvebkpachrffgfwuoprbstddmfsrfdpiazosajslsrleybslfptzefnidvtmdfewewfptonzmhdeesslkchbwreueoxskjksecxdkhncscnfnla",
            "ur:crypto-psbt/4-13/lpaabtcfahurcybgbztejnhdjylffeetbkassrhtksjstyieluurfwnldelreekkfyzeimskzemyteflievemtesluglythdcxqzsfzcmnpkdwtkiyhpvltaplgrnsdidsbnryeysfknonreckbnmnrduygoidzegasgenwegtgygaursogspdhybybdvylnjyvdieuocljekidyvagszmhfnlcybesbcxvorhoninfmbaghstjtdnbgmnmohgbsutuehdnyle",
            "ur:crypto-psbt/5-13/lpahbtcfahurcybgbztejnhdjybeytrlvoswfzmocxmnoepfkpeonsmdvdwmgwbzknuyvynnnlgoswtkstrthgjkprhnhkdpuylbhgiyhklkykhllsgrneieladaesoyzedrpfvlnsiondiyrtldfsmdwladoncerdcxpawpfhtpfnvsftclrhkpjkdayacxghrywlzmgyuelkntvlyajelecejkfptbbestrnchoxtbkgtijyltluftwptlghutsefgnldioe",
            "ur:crypto-psbt/6-13/lpambtcfahurcybgbztejnhdjyrflakihhspfxdnzcdifxnnlolotsckgodakpmelarfkeltjpieryhfrkjshgmkjzvlwpoyltatdnrffzgadygdbsinghdyvolyglqzwenltkjktsqdyaotghlkgmlridlfdrherngyjywzfzoyntguemmywlroynqdflmedrgssswspklfwmdptobktbfdmwkeroiabgehbwhnyalozomndtsgbktpqddrvlwpnyclmweorh",
            "ur:crypto-psbt/7-13/lpatbtcfahurcybgbztejnhdjylojpgahfwkgaenvwdkrkaetlfdnybkroahrnmnssjerscwrefmjpdkctvddafrsstsprsspkfzsoeoytiocefnurwfhgiyayzewfzoihsghsgegehscepddklanlpmgmmwdkmdtnpfdksnbtrehdzegsceztfslbcnwzbbaofrhlntaezmgrurmockgectbwwtdevskiotcyursbfwbgrsnnylchayrsglryfzcnjylsmtmy",
            "ur:crypto-psbt/8-13/lpaybtcfahurcybgbztejnhdjyihecsbmhenlfrkcflbsfdizswyvwuymylkjzkekemkehinesgrryynludmcttsreamjzrthgkgluoegyykeobnckisfelajtwnvovsbgmdvthpihjylrwlsnfyoydkbkwylknyhdmujyjtqziycfsorhayynueuejnfgnsctismnjeglidlokpmovdmdintbaocxpyetndlgjenbgensbedngdimwsampeiofeuopthsgmva",
            "ur:crypto-psbt/9-13/lpasbtcfahurcybgbztejnhdjymkwsueahmkinvdasfzbstaecftrhkefzadimhphgpfhlfxykmotsoyjpkkcmssdkzeyajokbdlgrftldglvannoeotwesthnldvajkzmftlykgrtytemtsbeoydwdmksswvagdmhtttycpldverptezehygmkndegecahloxceprlrgokbfdioeebwlysfzsjsencnzsrnehaxosolvtldmhneltrhtkpljoeneynbdyzowp",
            "ur:crypto-psbt/10-13/lpbkbtcfahurcybgbztejnhdjycpckutotwklkcemttoykcyjktbpmzmhtsbbeytotbelossaepedntndmkplpmhkioskorkrnzegeqzgdvlimsogwcagmjoechyoltiidjlmdkihhwmvlinsaqdfyjlftnewtaauobwlevyvoknstrsntsrjtkpwfjkpkbnwevaqzoybzsshgdapetotngwahmnfehfhdlycegrdlsnynbzbynymolfvynyhgvyjssewepthh",
            "ur:crypto-psbt/11-13/lpbdbtcfahurcybgbztejnhdjyjegobaaeryeezmrhlyrocyhtmnpradpfismelelbdnvwcmaxswmhkidlyljtesfmsppsndhgjodncxgeuomwecntgtzsatltrkfhdmkeynlofejlselasrwnfpehqdsondpfberlldwtvojofwiyjpbbbtidcloxfwfzrdemoltlhfwkhgrfcnctskisnbpyhgckdeclsgfrtyutimjzcawloxqdrkwfndjkotclmuvyzmmu",
            "ur:crypto-psbt/12-13/lpbnbtcfahurcybgbztejnhdjyssswehtbpyvlvtmshnheatoefzkomwzsamsgrovahedswzdnknfryklyeckkgubwwthtykzcehtaprchloveoylkjkztytbndshhltlnlahkcnsbmnsfoelygurhdrfduomwwnykprsksrfyfygtuojouorlwylrdirlimhtlybsgonejsrdgrgrbshpknheglkkpywygsbyqzosdyjltnkofmkpaowfcyoeseoxckskadcp",
            "ur:crypto-psbt/13-13/lpbtbtcfahurcybgbztejnhdjyismdoeiagavtdwhkgogdldhhcpbzuorkjojzmkpskgpdjswmdlssqdglcnwdctgemekgmsretkndeegavefdmeonflkojotyvosbynotvstbdnfzcyzskowtkghngatlpymseccmgmsfaturgyrhseamrnpelogorfiasgrttihhfeasfrdmqzmwjttafsjyrhaowfwyprtnzcaoahuefnplnntlknaeaeaeaeaerskgksah",
            "ur:crypto-psbt/14-13/lpbabtcfahurcybgbztejnhdjysemncpwkdknynsonpsjepllblpuovtzoeyinlkntfxpriynbfeetptdtykgonsadrowyhnonvddyeeechkhetboysbtteozszobasnhflsvawptdfptddwbeetkgpmwesknbjkknkghpgwltcnknmwisaxurzsjseodylprlhgamqzemamvdsptscygsdrhsloknwemspdmypkdiwlrnlntpcxcwjseemkgsrtndcswnbago",
            "ur:crypto-psbt/15-13/lpbsbtcfahurcybgbztejnhdjynbhpvwptzcgwfgwyrsclkbyaaswsiyjpgwdklavdwyvoadzecxyantgemebwptynwlmyvlnngelngauegafgamdtfgpaptndbzftylfyiadegwmhmefguouorssgtlvysgmozsmdtarfbnpednmnhnghmyswdnvoksgrlenladtikieepedrbzgalfdacfnsmwgontksiaonwyfgwfyafptbwkrhmhjlayaewypsgawpgrvo",
            "ur:crypto-psbt/16-13/lpbebtcfahurcybgbztejnhdjybahnskmhlurpfynbzejyfsnbhnhgtnfhvezcynaxqdtylbftlgdpluoxtajswylutorthpaebdnblfcwdtoseslsdarsltwlgeutswjtiaisckbkreaadrfnahmhmssrkpfnlewscylrlkssdklbrkpmahmwzmkndlamdsdetohpfsrdeceehflgcpzcsokigofmlscfgyrprskicxwtbtsawktaghykeebbvazclffrhnmd",
            "ur:crypto-psbt/17-13/lpbybtcfahurcybgbztejnhdjyhkahuofzcmmuethnamjpckurzetnlboxuomkchcnwfwpvllkdmlkfwwntddityktbsahldtytpplhsmdprrprlzcjkcfehjpcxssmssniaemfeaaflgovowkehurmdhfgdclzscywmtojtreglcklfckfmnyprkiecftpavyttprtbiydicxleolztjnrsjtbdbwtezssfdnidfdkitswleygdltfxioaxonhlkkcwksjtwp",
            "ur:crypto-psbt/18-13/lpbgbtcfahurcybgbztejnhdjydyjpwmiowevtbtoecaleaanehgwldrestaaenlyavstecejyvlmesaglbkntlktlgubstidmlrmwhgguvocfwdleesfliepkzcwehnfngdemfrsgonpamdbtzepytonbiedyndrlqzbgimpmbwdpglaxwnpewkoyeoottnnnhyhybacyrhreaaadprjoskgumunnchlauthynbcxaxwfrhftqdclgwdpbzsglpeypecldiwk",
            "ur:crypto-psbt/19-13/lpbwbtcfahurcybgbztejnhdjylffeetbkassrhtksjstyieluurfwnldelreekkfyzeimskzemyteflievemtesluglythdcxqzsfzcmnpkdwtkiyhpvltaplgrnsdidsbnryeysfknonreckbnmnrduygoidzegasgenwegtgygaursogspdhybybdvylnjyvdieuocljekidyvagszmhfnlcybesbcxvorhoninfmbaghstjtdnbgmnmohgbsutsfrdbaoy",
            "ur:crypto-psbt/20-13/lpbbbtcfahurcybgbztejnhdjynycklbmowedadittylssckessoueuruychplwynemuvesrsejpspfnlbmkfsdijzcnsbpeftftchtizsiyjlctcytsfwjpmshlrogrfryksrbnuodlglghwmsnkobnspjlvebkpachrffgfwuoprbstddmfsrfdpiazosajslsrleybslfptzefnidvtmdfewewfptonzmhdeesslkchbwreueoxskjksecxdkhnbksepdpy",
            "ur:crypto-psbt/21-13/lpbzbtcfahurcybgbztejnhdjytyfhlneejnotjprlwyzcrltsjkwdadcawelppmnslrstjzprdlzcftfgykdmcxoymhaxtpdsglmntyglaabyztbsetiantlkaxihdskspkwlrthgwlhgssfptnlrrsoyutehwegwmojydlkgnsjseegezcbandylaogwgebafnvapktopeentbpdyldywtfxfsetkizelupeotaxvabbbkaorhzeetcttkynceihfhmtmycx",
            "ur:crypto-psbt/22-13/lpcmbtcfahurcybgbztejnhdjypkmyvwpylrlukgsacfzeecfrfsreasatqdpfnbtlbwmtwsryldfgglcahefltkfsrysaferhdljocmatlugyfdasmnaxpkgmfmhdfhkeuywztdeypkglfzmhwldkahdrrpvehkgrlufdsgcyytjkamvsoneefsfmrslprksgwfwpgufzdkrscpytcxpfwfrfzmurgtcaenhemocfaolgetyaidsrldrkhpgwuesadkylihga",
            "ur:crypto-psbt/23-13/lpchbtcfahurcybgbztejnhdjyrflakihhspfxdnzcdifxnnlolotsckgodakpmelarfkeltjpieryhfrkjshgmkjzvlwpoyltatdnrffzgadygdbsinghdyvolyglqzwenltkjktsqdyaotghlkgmlridlfdrherngyjywzfzoyntguemmywlroynqdflmedrgssswspklfwmdptobktbfdmwkeroiabgehbwhnyalozomndtsgbktpqddrvlwpnyndkonlet",
            "ur:crypto-psbt/24-13/lpcscsbtcfahurcybgbztejnhdjymotppehkihvllumwbzhnryjepmhswstilavtpstsdalontrncnkosaasknonswjeioqdwpwkuyglwkzelstbjncwbdhkksswfnldperfzthlaxfgvomnsgdrdpfreswnatnbfraxpyswaosoaeoswzkgbaamcffgidbatktdrtluskjywdkblofzfpeelpjondbaaeoszmmokkahtnztidsoamrkotzejpnykksfbaesecyt",
            "ur:crypto-psbt/25-13/lpcscfbtcfahurcybgbztejnhdjyftlkprjtksdkfentdirkwsherdndlnrsntaycaknhfqziscedmpytopkiahtgtnlvwtbmkcaiohhghsnldionlbsvdqzrdtarpjlsfeyeocafmpduewpcsoxlpwfjzkiwpsgrsmnktdrkoztpmlpmnsschbzgadtytamzsdtbncecxaxdktekefxlstnpktiaemtdrvdlasavolnnlrlksmespknsfgootmhfeotiooxgsbz",
            "ur:crypto-psbt/26-13/lpcscybtcfahurcybgbztejnhdjyihecsbmhenlfrkcflbsfdizswyvwuymylkjzkekemkehinesgrryynludmcttsreamjzrthgkgluoegyykeobnckisfelajtwnvovsbgmdvthpihjylrwlsnfyoydkbkwylknyhdmujyjtqziycfsorhayynueuejnfgnsctismnjeglidlokpmovdmdintbaocxpyetndlgjenbgensbedngdimwsampeiofeuopmqdmhyn",
            "ur:crypto-psbt/27-13/lpcscwbtcfahurcybgbztejnhdjyssswehtbpyvlvtmshnheatoefzkomwzsamsgrovahedswzdnknfryklyeckkgubwwthtykzcehtaprchloveoylkjkztytbndshhltlnlahkcnsbmnsfoelygurhdrfduomwwnykprsksrfyfygtuojouorlwylrdirlimhtlybsgonejsrdgrgrbshpknheglkkpywygsbyqzosdyjltnkofmkpaowfcyoeseoxvachvosn",
            "ur:crypto-psbt/28-13/lpcscebtcfahurcybgbztejnhdjyrflakihhspfxdnzcdifxnnlolotsckgodakpmelarfkeltjpieryhfrkjshgmkjzvlwpoyltatdnrffzgadygdbsinghdyvolyglqzwenltkjktsqdyaotghlkgmlridlfdrherngyjywzfzoyntguemmywlroynqdflmedrgssswspklfwmdptobktbfdmwkeroiabgehbwhnyalozomndtsgbktpqddrvlwpnyhssphyjo",
            "ur:crypto-psbt/29-13/lpcscabtcfahurcybgbztejnhdjyrdmdcnchswlproguiewppktepmpyhnahdavyidcanbtbfpdwssneehyngmyltpbdbentgmtksetyvywtaylspdbwonnyjprylpjzleytmyfytyleweieosdsrdvthglybkjslgrflsctrttichotptwmzednfszmkpnbetrkhnaesrfyvwmhiouedezotnbkbtdwisecvwrolsfzhkneknzowdbapfpradrnkemujztnbyya",
            "ur:crypto-psbt/30-13/lpcsckbtcfahurcybgbztejnhdjybzpaoxrtgaeswybgfwmtcfpdynenvyvaurwpcladstkpbkbgimskmujlaekgrfnbdewerolsptrnvtkghlgldrplwfprplkoyajewmdmdtbsdwlppdlpvsgyvozmbggscsmennecwldlhnztrsbdnbcpzetthslnehlbtkrhjevtckytptttcklbweketncprefwmhincncxasdkbspklktnnlvwgadnhgrduozeghwnhtga",
            "ur:crypto-psbt/31-13/lpcsctbtcfahurcybgbztejnhdjyoteyprfhheisrtfykoiabsurdrmhvdrynypachlknejzfwvycpbbglrpytgolyrnpemtsnpkylpfmsiezesbksvykecwmsgmdlonoeidplemoldnytntkbeyfxoxclfhcxldtdzsurkgpmjnbdwewpytjzpfcpmhvyyklrdewyryurbnskrogronmundimreaoksctrecslutkgwvltdbzsbdsgshkptrlkelalfsgntwdtk",
            "ur:crypto-psbt/32-13/lpcscxbtcfahurcybgbztejnhdjyskbbgtkeplecoeckwtsfkeecrfeecmmykpwespgwfyjkdazmjejlcmsesnehwzpyrhroceynbkswutsomsmtesmdjntaltsteczojkvadlfxuruootkbbzbturgrecolyalyfgetltktoxtlhkknwkktihqdwksactpedshlfglnnejnzeimntbndapycamdrybgasfghsrtpsvayafxwfiagekorfhkhsdkbsflssndrlny",
            "ur:crypto-psbt/33-13/lpcsclbtcfahurcybgbztejnhdjywtsoemkouofhpfcwgecxzsrteseteeneaocllrdptdvdtlahseoeaeotfwremwlpcmgebngtsftbykjldiaxrfnlttwnksgufyvlnloypldrvagtskmdbyjlascncmqzyagmbntayllgurldzohpplcxqzmhdpihckimbzkbjngtdijklbzerhfphyhhrdhdnyiawndeldfnwsoyaafpfsgmbtmhnlrpaolscaimmhfwksvs",
            "ur:crypto-psbt/34-13/lpcscpbtcfahurcybgbztejnhdjycpckutotwklkcemttoykcyjktbpmzmhtsbbeytotbelossaepedntndmkplpmhkioskorkrnzegeqzgdvlimsogwcagmjoechyoltiidjlmdkihhwmvlinsaqdfyjlftnewtaauobwlevyvoknstrsntsrjtkpwfjkpkbnwevaqzoybzsshgdapetotngwahmnfehfhdlycegrdlsnynbzbynymolfvynyhgvyjsolcsspie",
            "ur:crypto-psbt/35-13/lpcscnbtcfahurcybgbztejnhdjyfsadcydkoyldmhfpcyntjndlgwzmtnrkhysnsffycsdtjninjzknvdcmsomsswzelglgnydaassofsqzhgtinniaihlbjkrohhlknessdisbatgmfmtdfssrcnkpinwtdiloctsaihwylyihaxuodknlyktoaxhgmyrhsauogdpfcsmytluywnrlsedebgtpkgwddtgmjtdmchghjpbafwkgkgpdbydmhdvypttbfpgdhyba",
            "ur:crypto-psbt/36-13/lpcsdkbtcfahurcybgbztejnhdjykgecpyytnysaiasesflunblpfemosrrtfdihkeiapkahykpkluialbsslovloevteopdqdckonfyvyzojyonvadadagljshtttdeylgakgrelozertcmaahpentldadlzordvoregscsdmjyrohplalgqzzecxndlfstasntcyvygwvdonhpndwslgetvesamnleeybslaeewplrjohkzcjysauyfpsgatseonfldkgsrtdr",
            "ur:crypto-psbt/37-13/lpcsdabtcfahurcybgbztejnhdjykbcajekgdnamjtcwjpgrievdkpgetybwjydwrnoerfvtwsykcnfxgyjtnnkknbsngrfztidtiyvahnldlkadsbssuyaomuvlmeztfzdpbaryrymsuogtbtrpjziocfcejsbacphyvyhgtnsbrpincmislgsrgebdbwjedryluyktjopruthffgcpfpfhkeaxgaloeoglkghtsatbdyfdzejptbfpotjssbmhvacsuehyhemt",
            "ur:crypto-psbt/38-13/lpcsdsbtcfahurcybgbztejnhdjycpckutotwklkcemttoykcyjktbpmzmhtsbbeytotbelossaepedntndmkplpmhkioskorkrnzegeqzgdvlimsogwcagmjoechyoltiidjlmdkihhwmvlinsaqdfyjlftnewtaauobwlevyvoknstrsntsrjtkpwfjkpkbnwevaqzoybzsshgdapetotngwahmnfehfhdlycegrdlsnynbzbynymolfvynyhgvyjsinvdcepd",
            "ur:crypto-psbt/39-13/lpcsdibtcfahurcybgbztejnhdjymkoyosrsueastttbenylbeotoedmhhpshfjnfhbawfgtctatbsdyeejelavywswngolbatihrhnbasvdfwlahhytplldureyrkdrfmpmfrclehcmfgtduraddkbggepmhsbtbaltadhkzotbadehpfbtwnbtvlhdbdhsdaprdssgidfnemeebtlskpckbepskgwmtejljzsaynmtzmhdtsjlntrypdzmkbztjncadtlughjt",
            "ur:crypto-psbt/40-13/lpcsdebtcfahurcybgbztejnhdjyfpmsqdlgnyfrhsjewsfnwmwfmowpamfplelawfzsretigwmhpebnhfkpssyaasmugtonpkktfprolyaytprkvdryldzmzonnspbtluntfhrsamwtjphtmumoatismtbycncwfpfdmyjtytpynlhhqdflwdvymnosrsmtfldafrcyhgjostoxtegepajnhtrklprtpefesnyksbvydtmtlrpraojlbtteftidytpyhgbdktfh",
        )),
        Vector("odd", "46429b555da50456de44b788521d9ca8ded1f16ff143c058a201518da03f8461bd6854fa698cec11be28feeadf1365e2c8922d48e4d48551f85f495b37281ad59a713a81cc8a93de332ab7f602f4ade35251deb09ecd79873b841e419962abc65e3c72d6c0f940359a66ee3c23646f56548294f75039ecf6625d867167827a9fbb50782cf316cb140219820b9b6abae9dc3c36b0a308c31e15fb0cdf3581e0d197d71cf68504a3fd30a7297cba72af4f2b3cfcb2b20e7502b4930d418dbd4ad00e9f7fef0cc9c9f59c976a68c149863eb6e073180c050d294f7ebd15e9f538166e191b129250d3877094a16ce7258972242d7d41b5882ba0ad38209abc51401e3a5bb8a19467eabe8e6bffeb579b17a755cb4fd0241703f5486111d568590a692a1f782103c0cd2e8a10870b2ea8a3a2570471ac44175c3c4ec03f8d47fb517d29c2cd5b44f1ccebaa9a13b680aab32771cac20076d0920da03c2b4ea587e8e054477d5d3275cd8feb444d9a5266e2031ab0713b8b6f9583d4e32c69c2b3b320ff54a5761aaa4b37fd6cb728a001df0401ed0510cd03e83e35e56bf1d398aacdb131b33dbcd352854495a49856086cfb065e5776f681a461c097e7bf01b5da7c26cd6b894f14849fd8663ce3596a5fc03d8293a835c173952771736e66e964c64abae4797c7a182f84a97cc77e79a52df07c3b262e809503d011b35f157a7d46432d886d5ff2d660c94e34c68fee3a219c1623b2f95ddf5cd53823b611116c9e6600180b8c4e1de6da6707264ce4337ef1ba765320deb7d404c9115a52bd8f713eb1a53a0645ddf69f2b6ff166d9b42ad478b054a4691922dfce9ef8c6ed566a8b61982948709e48b5f5b7f684b2fb115f05a345f74d96819c74c1ff4d7747333fd7ac483e4f771c6b44ffc540b55321deb773f801b850b1240c44a5ba96f97d827def4d4b441832c4b93cb6288d224bd38b9a841dbffff6150ac5e0d7e519fead5fc5304394c1cf7a1d36e3c04bdc88f120e5451c5bef17c206b66d5f66696d5130e52e706a48bef85a89287e610ed122bb75d93f67005f30bf5ecb047ebc882f465ac990d5174c5d", 60, listOf(
            "ur:crypto-psbt/1-13/lpadbtcfaxbncyfxluqzoyhdfnhkaxasfgfwndgohlonaahfuefyrllogmcanspduettwnjlwnfxrthdoeadgylgnbfhlrhsryisghzsinlkwpbyrndezewdurbwihvospmodpfdvetylpgyyakndtptkk",
            "ur:crypto-psbt/2-13/lpaobtcfaxbncyfxluqzoyhdfnhegahpemdecytlnyjsftlysflemuueeodrrlynaowkpmvlgmgyuepfnnsnkkltfrlrckfpnlidpyswhyfnjptbrtytfzecnyiywyfncniejlhfghlfmwylgdltcshplk",
            "ur:crypto-psbt/3-13/lpaxbtcfaxbncyfxluqzoyhdfneswpynidhllnjsiolfknnerkgdksdwwfcmsbbbaocflfbdndimrdwluofnenpfotaysrckbzzobnureclyvtttmstsceynlpaaotzcdyosdtkerdjppegwdnehdnenrp",
            "ur:crypto-psbt/4-13/lpaabtcfaxbncyfxluqzoyhdfnfnztprprbakpaoqzmubtfplgrygetibanelbwsbnsosoyknsmsimissegalnfmrpvtjkcsbnahbtdtgwkbrybzwlyketcmjtcfcwbgmogdteltjomwoyjzvdwnwyprsr",
            "ur:crypto-psbt/5-13/lpahbtcfaxbncyfxluqzoyhdfndaldjpdkdpkifprelodnnbpmetcxnyrfgyfzckfthprooymwiowdrnmnjezmwmhgndchosgosbgwtidkchaxykfdhsbytlishkbkindrctksclaxrtsndmlejecymkvs",
            "ur:crypto-psbt/6-13/lpambtcfaxbncyfxluqzoyhdfnbeltbddmpdotoehgaajspsfychhhfnglrtfhlgflzogykidtsasnhpfywnsfwmpknybwrplapkqddijssgsaaekotimobtnbfndnglonltvsvtghflkihleyecbyetyk",
            "ur:crypto-psbt/7-13/lpatbtcfaxbncyfxluqzoyhdfnkpsnmywmfygtnygmiyvoaxcypfjsfrlujlmdlstyvldwinsaqdqdcxzmghonkocypkgremzcjzrldenbaduraaadweahbesnaxvsfmecvwjewntemkpksnpagtpduobt",
            "ur:crypto-psbt/8-13/lpaybtcfaxbncyfxluqzoyhdfnehqdfsrftegmlpfymdoxmkhfayjzzoamhyhgkoynlyoxhsrtmsvdrsadretnkedssnjeldgwbblrnetpiyfnvlhkimhertfslfmupdecsejkmddijsjkjtiyftvacept",
            "ur:crypto-psbt/9-13/lpasbtcfaxbncyfxluqzoyhdfnwlieswgerdvekkkekncsdllrptkestkbkkondpwtkefrdsdmlamdaxtibyqdhebzknkifgfxdplojnhewztbhnsogleeswmywyftclnscmcnprythlurhhtlluhelstn",
            "ur:crypto-psbt/10-13/lpbkbtcfaxbncyfxluqzoyhdfnetcnrpbybyjznniyaecsbdlkglcavatnioatdsgsveeokbwnrdkogucxuerltyaasobyhtgmrymyjsfmpaonftamfeutynnednjlwniytaqzdrtykspfghoxksswsrch",
            "ur:crypto-psbt/11-13/lpbdbtcfaxbncyfxluqzoyhdfnincfcpurtonnyaswwehfimluhsmkdtfdjonnfdreykrlynlrprzobyheahotfeylgtmtlynsjysezmgtktfleofhtspsfdfmgwktcejefyzmskfzregucluewedwguvw",
            "ur:crypto-psbt/12-13/lpbnbtcfaxbncyfxluqzoyhdfnrljkyaadrogdpadkbnfyonrdmtytkilfkiwsgtgrfycseyssrhfnrpdelgcpgrtelunylrcarszmynbzbkskvttsvwcfzepmheskdyfxmwsetkkncaenvlrtbygsiawd",
            "ur:crypto-psbt/13-13/lpbtbtcfaxbncyfxluqzoyhdfngruolowncxvwfecehpwschsaamrpjnheiyinjngydyvwdmjoimfdrnyahtlddekbhsbattcprkkptafhioaehedyrshysbaakbrflodlfghtsomhtlchgshlcafweopa",
            "ur:crypto-psbt/14-13/lpbabtcfaxbncyfxluqzoyhdfncwkponayimztmhkibnkobtkewkbdlbwkdnmwgefezmbnimuyueykmscpclkslrdpswbyweglgaayimesfrdnolrnhkdnjetduyrogylsbydsurvddsiypeckeeosmkle",
            "ur:crypto-psbt/15-13/lpbsbtcfaxbncyfxluqzoyhdfnetcnrpbybyjznniyaecsbdlkglcavatnioatdsgsveeokbwnrdkogucxuerltyaasobyhtgmrymyjsfmpaonftamfeutynnednjlwniytaqzdrtykspfghoxaxjktybe",
            "ur:crypto-psbt/16-13/lpbebtcfaxbncyfxluqzoyhdfnsfweqzjtmsnletsowzeomydtmehhhlsadevweosgdilsltrdvdlbryhykngsqzfwvyimvycmvastrykgvwtlmdlydldabwvdrldyfdrpashpmuzsntbgjphehdhfcacn",
            "ur:crypto-psbt/17-13/lpbybtcfaxbncyfxluqzoyhdfnfeidndfxisfdhddakndrltfwsbmnskaydluedrzestdlkbehvdhhcptnrywlmhwmemyndlbeuylbwnjksretdsnbwzamguwtimflfzhhmussoeieiymybebdrhskfdkn",
            "ur:crypto-psbt/18-13/lpbgbtcfaxbncyfxluqzoyhdfnetcnrpbybyjznniyaecsbdlkglcavatnioatdsgsveeokbwnrdkogucxuerltyaasobyhtgmrymyjsfmpaonftamfeutynnednjlwniytaqzdrtykspfghoxvstigmrn",
            "ur:crypto-psbt/19-13/lpbwbtcfaxbncyfxluqzoyhdfngdfyzctkindyuyvdwysootrllogyoyemfmtlntwyromwsphftyhknnjsfhhtntgtehhhmhpdosyayalrcmuowngalkbbskonhtvohgctzsbwtitihdiovlfrmdcftnmw",
            "ur:crypto-psbt/20-13/lpbbbtcfaxbncyfxluqzoyhdfngruolowncxvwfecehpwschsaamrpjnheiyinjngydyvwdmjoimfdrnyahtlddekbhsbattcprkkptafhioaehedyrshysbaakbrflodlfghtsomhtlchgshlcnhewnlk",
            "ur:crypto-psbt/21-13/lpbzbtcfaxbncyfxluqzoyhdfnpyaysojessgultstpfpkiduezokkrymscldpkpregstsnbwkjkwslohsmdwnfzbkurdnlkutoynsjsihhfbefdaymovtgtmdtimeiobasojetiastbwzwkgliyospdue",
            "ur:crypto-psbt/22-13/lpcmbtcfaxbncyfxluqzoyhdfnahrstpmnoncndioxdsdpjosrgyhpdmdnsgkiaymetoctwfjsengrmnwkfnihrpclhpuororoyabbolvdtlbehpnejljybdcxuejknypabdsstidraeeotpdpfhpehgby",
            "ur:crypto-psbt/23-13/lpchbtcfaxbncyfxluqzoyhdfnykctylcxswvdgwneswynvsvtssztatjedaiobzbajywkqzckgefgfsnlashnghsfdmsfgllseowmwddlplaxspcmessnkprlhsjtkottgrldpmlemtcwgrhpaeiyzela",
            "ur:crypto-psbt/24-13/lpcscsbtcfaxbncyfxluqzoyhdfnmnnebaiavwtbrtfxmnfmftadswlygyjsjedkhkgahlnyeshetelnhewkpabbzojolshknyayfywfdtcxludaehfzeyahaydehpiysnjkeovsqdrtjlnlpswmtdjtrhst",
            "ur:crypto-psbt/25-13/lpcscfbtcfaxbncyfxluqzoyhdfnaxlginlptivdiylkcwetjlmsptlynndaoxlnrthdytvwsgeygoswfxtewpzefnfphkloaaecmohkdsfgaoimftpdkswzdskolofdutsnjsfpsaryecfmbtihkpnsamlo",
            "ur:crypto-psbt/26-13/lpcscybtcfaxbncyfxluqzoyhdfntdcnurpyaojtprrdbtchcecetejnylayrfskytamfwjyonurolbtldoelkcxrtwnvdadfxbttyqdkppltdtakghskpmywltsmtjorebkpkaddnbarkdldafmbnfggliy",
            "ur:crypto-psbt/27-13/lpcscwbtcfaxbncyfxluqzoyhdfnbeltbddmpdotoehgaajspsfychhhfnglrtfhlgflzogykidtsasnhpfywnsfwmpknybwrplapkqddijssgsaaekotimobtnbfndnglonltvsvtghflkihleysnsodtgr",
            "ur:crypto-psbt/28-13/lpcscebtcfaxbncyfxluqzoyhdfnahbefytiguwfjktebyktueenweeyztzcldqzzobatigrzeatzctilycakppfmnbzvspfamcfzeadynknzmhlsskbcpdkvtwmcarowsoeylzszosgvabacnsfmslbotcy",
            "ur:crypto-psbt/29-13/lpcscabtcfaxbncyfxluqzoyhdfnbgurlyrlidkbbefpzewmfpcefwadvwbtkgykskmyvybbfplydtlovahthptponuehylepfneteclcnhfwmwpglmnmsnbcluyjntaimvdtyktlyjyadmocaonstrkmupt",
            "ur:crypto-psbt/30-13/lpcsckbtcfaxbncyfxluqzoyhdfncpdwjzhpmsknmnyntnmtdrsgzsotbnnnbecmchamvasbwsswbyndecdswtwtcydeclkibdmscfhkinzolsltktasjtiykeluwdoldwkkcmjpkiglsbftgtptaazoryvw",
            "ur:crypto-psbt/31-13/lpcsctbtcfaxbncyfxluqzoyhdfnehqdfsrftegmlpfymdoxmkhfayjzzoamhyhgkoynlyoxhsrtmsvdrsadretnkedssnjeldgwbblrnetpiyfnvlhkimhertfslfmupdecsejkmddijsjkjtiyecghcncf",
            "ur:crypto-psbt/32-13/lpcscxbtcfaxbncyfxluqzoyhdfnfnztprprbakpaoqzmubtfplgrygetibanelbwsbnsosoyknsmsimissegalnfmrpvtjkcsbnahbtdtgwkbrybzwlyketcmjtcfcwbgmogdteltjomwoyjzvddwjzhtnt",
            "ur:crypto-psbt/33-13/lpcsclbtcfaxbncyfxluqzoyhdfnfhjljtcwtdimidasfmletbisbybwetjscxpfcamokntaatmeahdlfwaswdidgmtnkbnnpmrhrhjyltisrpvyotfpgsfmnbsnfxplbnayssinatattpeschpmiyetsbdr",
            "ur:crypto-psbt/34-13/lpcscpbtcfaxbncyfxluqzoyhdfnbeltbddmpdotoehgaajspsfychhhfnglrtfhlgflzogykidtsasnhpfywnsfwmpknybwrplapkqddijssgsaaekotimobtnbfndnglonltvsvtghflkihleyvsmutipy",
            "ur:crypto-psbt/35-13/lpcscnbtcfaxbncyfxluqzoyhdfnwlieswgerdvekkkekncsdllrptkestkbkkondpwtkefrdsdmlamdaxtibyqdhebzknkifgfxdplojnhewztbhnsogleeswmywyftclnscmcnprythlurhhtljyasadcf",
            "ur:crypto-psbt/36-13/lpcsdkbtcfaxbncyfxluqzoyhdfnzctlbsprjyhejytllygunnfwtizslpjnflaxhpbzldasvdhdoljzytwktpmwdtpssswlwlqzwlghhltbiesfghhfdetstyjlfrayeewtbnkgmnmshlsncfmneochpkes",
            "ur:crypto-psbt/37-13/lpcsdabtcfaxbncyfxluqzoyhdfngovwmhjnrtwmzsjpkbhpdnamuotdytfgwsvyosrhfnkbaxcsdamekknngsdakgfppmvwnlmhjssftbaoaszsdstbcpmwhygdhfjzbaytbbdwfwdyclwzgtesckdlpfto",
            "ur:crypto-psbt/38-13/lpcsdsbtcfaxbncyfxluqzoyhdfndrlrtyuojzhggwspchtplftbftvovwrofecpkptbchlylemhvojnmhhsnluowncldmgokoiebacewyzefspmtdsebbfedahgihamaocmlyaaosltcyfmftvycmhpbkny",
            "ur:crypto-psbt/39-13/lpcsdibtcfaxbncyfxluqzoyhdfnaylkoerhfsinhhbycetimytyecvocshkdigsmyiyrtdyinaowynlgyahemzoeyykghpsfwrsbkflpdwejkfwcsoshtuereoefwrtmybspecwkkialnmdldckfswemkyt",
            "ur:crypto-psbt/40-13/lpcsdebtcfaxbncyfxluqzoyhdfngruolowncxvwfecehpwschsaamrpjnheiyinjngydyvwdmjoimfdrnyahtlddekbhsbattcprkkptafhioaehedyrshysbaakbrflodlfghtsomhtlchgshljtsblokg",
        )),
    )

    @Test
    fun theEncoderProducesTheReferenceStrings() {
        vectors.forEach { v ->
            val encoder = UrEncoder(Ur.ofBytes(Ur.PSBT, bytes(v.hex)), v.maxFragment)
            v.parts.forEachIndexed { i, expected -> assertEquals(expected, encoder.nextPart()) }
        }
    }

    @Test
    fun theDecoderReadsTheReferenceStringsInAnyOrder() {
        vectors.forEach { v ->
            val message = bytes(v.hex)
            listOf(v.parts, v.parts.reversed(), v.parts.shuffled(java.util.Random(5))).forEach { order ->
                val decoder = UrDecoder()
                order.forEach { decoder.receive(it) }
                assertTrue(decoder.isComplete)
                assertEquals(Ur.PSBT, decoder.result!!.type)
                assertArrayEquals(message, decoder.result!!.bytes())
            }
        }
    }

    @Test
    fun partsMayBeMissingAsLongAsEnoughMixedPartsFollow() {
        val random = java.util.Random(3)
        repeat(30) {
            val message = ByteArray(200 + random.nextInt(1500)).also { random.nextBytes(it) }
            val encoder = UrEncoder(Ur.ofBytes(Ur.PSBT, message), 90)
            val decoder = UrDecoder()
            var sent = 0
            while (!decoder.isComplete && sent < 2000) {
                val part = encoder.nextPart()
                sent++
                if (random.nextInt(100) >= 40) decoder.receive(part)
            }
            assertTrue(decoder.isComplete)
            assertArrayEquals(message, decoder.result!!.bytes())
        }
    }

    @Test
    fun aShortMessageIsOneString() {
        val message = ByteArray(40) { it.toByte() }
        val encoder = UrEncoder(Ur.ofBytes(Ur.PSBT, message), 100)
        assertTrue(encoder.isSinglePart)
        val text = encoder.nextPart()
        assertTrue(text.startsWith("ur:crypto-psbt/") && text.count { it == '/' } == 1)
        val decoder = UrDecoder()
        assertTrue(decoder.receive(text.uppercase()))
        assertArrayEquals(message, decoder.result!!.bytes())
    }

    @Test
    fun badInputIsRefusedWithoutThrowing() {
        val decoder = UrDecoder()
        listOf("", "bitcoin:abc", "ur:", "ur:crypto-psbt", "ur:crypto-psbt/zzzz", "ur:crypto-psbt/1-3/aeaeaeae", "ur:crypto-psbt/x-y/aeaeaeae", "ur:Crypto_Psbt/aeaeaeaeaeaeaeae", "ur:crypto-psbt/0-3/aeaeaeaeaeaeaeae")
            .forEach { assertFalse(decoder.receive(it)) }
        assertFalse(decoder.isComplete)
    }

    @Test
    fun aPartOfAnotherMessageOrTypeIsIgnored() {
        val a = UrEncoder(Ur.ofBytes(Ur.PSBT, ByteArray(300) { it.toByte() }), 100)
        val b = UrEncoder(Ur.ofBytes(Ur.PSBT, ByteArray(300) { (it * 7).toByte() }), 100)
        val other = UrEncoder(Ur.ofBytes("crypto-account", ByteArray(300) { it.toByte() }), 100)
        val decoder = UrDecoder()
        assertTrue(decoder.receive(a.nextPart()))
        assertFalse(decoder.receive(b.nextPart()))
        assertFalse(decoder.receive(other.nextPart()))
        repeat(40) { decoder.receive(a.nextPart()) }
        assertTrue(decoder.isComplete)
        assertArrayEquals(ByteArray(300) { it.toByte() }, decoder.result!!.bytes())
    }

    @Test
    fun aDamagedPartIsRefused() {
        val encoder = UrEncoder(Ur.ofBytes(Ur.PSBT, ByteArray(300) { it.toByte() }), 100)
        val part = encoder.nextPart()
        val damaged = part.substring(0, part.length - 6) + (if (part[part.length - 6] == 'a') "b" else "a") + part.takeLast(5)
        assertFalse(UrDecoder().receive(damaged))
    }

    @Test
    fun bytewordsKeepTheirContract() {
        val data = byteArrayOf(0, 1, 2, -128, -1)
        val text = Bytewords.encodeMinimal(data)
        assertEquals(2 * (data.size + 4), text.length)
        assertArrayEquals(data, Bytewords.decodeMinimal(text))
    }

    @Test
    fun cborRoundTrip() {
        val item = Cbor.Array(listOf(Cbor.Uint(0), Cbor.Uint(23), Cbor.Uint(24), Cbor.Uint(255), Cbor.Uint(256), Cbor.Uint(65536), Cbor.Uint(4294967296L), Cbor.Neg(-5), Cbor.Bytes(ByteArray(300)), Cbor.Text("ü"), Cbor.Map(listOf(Cbor.Uint(1) to Cbor.Tag(303, Cbor.Bytes(byteArrayOf(1))))), Cbor.Simple(21)))
        assertEquals(item, Cbor.decode(Cbor.encode(item)))
        assertEquals("5820" + "00".repeat(32), Cbor.bytes(ByteArray(32)).joinToString("") { "%02x".format(it) })
    }
}
