                                    ﻿_ 
  _  _ __ _ ___ __ _ _ _ ___ ___ __| |
 | || / _` / -_) _` | '_/ -_) -_) _` |
  \_,_\__, \___\__, |_| \___\___\__,_|
      |___/    |___/                 


Césaire Agonsè - Nicolas Atrax
Université Gustave eiffel
2023


UgeGreed est un système de calcul distribué au-dessus du protocole TCP.
Ainsi vous pouvez via le jar ugegreed, lancer une application qui peut se connecter à plusieurs applications ugegreed, lancer différentes conjectures sélectionnés et recevoir le résultat de celle-ci dans un fichier de destination.


UgeGreed utilise un protocole réseau en forme d’arbre.
Il y a une application ROOT, qui est la racine du réseau, qui sera la première à se connecter et la dernière à se déconnecter. Les autres applications seront des applications NORMAL qui sont des fils ou des petits fils de l’application ROOT.
Un nœud du réseau a un unique père mais peut avoir plusieurs fils.


L’application utilise des conjectures qui implémentent l’interface fr.uge.ugegreed.Checker. Pour que l’application fonctionne, il faudra alors que tous les fichiers des répertoires src, bin et JarTest soient présents et se trouvent dans le même répertoire que le jar au moment de l'exécution.
Vous pouvez utiliser les jar en connaissant leurs url http.
Si vous voulez utiliser vos propres conjectures, il faut placer son jar dans le dossier JarTest.


Pour plus de précision sur le protocole TCP utilisé, veuillez consulter la RFC de l’application.


DÉMARRAGE:


L’application se lance via une commande dans le terminal en utilisant le jar donné et utilise java 19 : 
java -jar --enable-preview UgeGreed.jar


Sur la même ligne de commande il est ensuite possible de lancer l’application :
- en mode ROOT : listenPort outFolder
- en mode NORMAL : listenPort outFolder hostname connexionPort


- listenPort étant le port publique d’écoute de l’application.
- outFolder étant le chemin du répertoire dans lequel seront écrits les fichiers contenant les résultats des conjectures.
- hostname étant le nom d’host de l’application mère ou l’application fille doit se connecter.
- connexion étant le port publique d’écoute de l’application mère ou l’application fille doit se connecter.


FONCTIONNALITÉS:


Une fois démarré, l'application affichera quelques logs indiquant que tout est opérationnel.
L’utilisateur peut alors rentrer plusieurs commandes :


- ROOT (ou R) qui permet d’afficher la table de routage de l’application et ainsi de voire toutes les applications dans le réseau. Les nœuds du réseau directement reliés à l’application seront précisés par un nom entre parenthèses. son pour un fils ou father pour le père de l’application, dans le cas contraire “not linked” sera indiqué à la place.
Elle affichera pour chaque noeud dans le réseau, excepté le représentant lui même:
A ⇒ B 
- A étant l’adresse du nœud en question.
- B étant l’adresse du noeud la plus proche connectée à l’application pour rejoindre A.


- INFO pour avoir quelques informations sur le statut de l’application. La commande affiche au moment ou elle est exécutée, entre autre, dans l’ordre :
- le mode de l’application
- l’adresse du père si le mode est NORMAL
- le nombre de fils connecté directement à l’application
- le nombre de noeuds connectés dans le réseau (sans compter le noeud qui lance la commande)
- si l’application est en mesure d’accepter un nouveau noeud
- si l’application est en état de redirection
- le nombre de calcul de lancé par l’application
- l’id de l’application ( donc son hostname et son numéro de port publique d’écoute)


- DISCONNECT (ou D) permet de se déconnecter du réseau. La commande envoie d’abord une demande à son père une autorisation, puis si la réponse est positive ou qu’aucune conjecture n’est en train d’être effectué sur l’application est faite , l’application se déconnecte.
Si l’application est en mode ROOT alors il faut que toutes ses fils soient déconnectés.
Une fois que l’application est déconnectée, celle-ci s’arrête.


-PAUSE éventuellement pour changer l'état qui indique si l’application est en mesure d’accepter un nouveau noeud.
/!\ ATTENTION A N’UTILISER UNIQUEMENT SI VOUS SAVEZ CE QUE VOUS FAITES /!\


- START url-jar fully-qualified-name start-range end-range filename
- url-jar étant l’url du jar à conjecturer. Il peut avoir la forme d’une url http ou alors d’un chemin sur le disque.
- fully-qualified-name étant le nom complet du jar qui implémente l’interface fr.uge.ugegree.Checker.
- start-range étant la première valeur de la plage à tester.
- end-range étant la dernière valeur de la plage à tester.
- filename étant le nom du fichier se situant dans le dossier <outFolder> dans lequel les résultats vont être collectés. 
Cette commande lance une conjecture sur tout le réseau. Sur de grosses conjectures, l’opération risque de prendre du temps. Quand le résultat est conjecturé celui-ci est alors écrit dans le fichier filename et une notification apparaît et alerte l’utilisateur.
L’utilisateur peut entrer d'autres commandes en attendant le résultat et ainsi lancer plusieurs conjectures à la suite. L’application empêchera l’action si celle-ci gère plus de 5 conjectures simultanés.


EXEMPLE :


Une application en mode ROOT peut lancer la commande :
java -jar –enable-preview UgeGreed.jar 7777 out


Les applications filles vont pour lancer dans cet ordre :
java -jar –enable-preview UgeGreed.jar 7778 out localhost 7777
java -jar –enable-preview UgeGreed.jar 7787 out localhost 7777
java -jar –enable-preview UgeGreed.jar 7779 out localhost 7778


Ainsi l’arbre du réseau sera:

7777
├── 7778
│   └── 7779
└── 7787
n’importe quelle application peut lancer une conjecture comme :
START ./JarTest/Collatz.Jar fr.uge.collatz.Collatz 1 15 result15

L’application qui a lancé la commande recevra sur son écran une notification qui indique que la conjecture a été effectuée. Les plages de valeurs 1 à 14 seront sauvegardées dans le fichier result15 dans le répertoire out.
