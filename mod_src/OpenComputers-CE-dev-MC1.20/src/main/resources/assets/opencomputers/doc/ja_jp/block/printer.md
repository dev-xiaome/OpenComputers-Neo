# 3Dプリンター

![2Dプリントはもう古い。](oredict:opencomputers:printer)

3Dプリンターを使用すると、任意の形状、任意のテクスチャを持つブロックをプリントできます。3Dプリンターを使い始めるには、コンピューターの隣に 3Dプリンターブロックを設置する必要があります。これにより、`printer3d` コンポーネント API にアクセスできるようになり、提供されている関数を使用して[モデル](print.md)をセットアップしてプリントできるようになります。

3Dプリンターをセットアップするより便利な方法は、Open Programs Package Manager (OPPM) を使用することです。インストール後（`oppm install oppm`）、[コンピューター](../general/computer.md)に[インターネットカード](../item/internetCard.md)が搭載されていることを確認し、次のコマンドを実行します：
`oppm install print3d-examples`

例となるモデルは `/usr/share/models/` に .3dm ファイルとして保存されます。利用可能なオプションについては、これらの例、特に `example.3dm` ファイルを確認してください。あるいは、`wget` と[インターネットカード](../item/internetCard.md)を使用して OpenPrograms から `print3d` および `print3d-examples` プログラムをダウンロードすることもできます。

モデルをプリントするには、[コンピューター](../general/computer.md)を介して3Dプリンターを構成する必要があります。ノンストップでプリントするように設定すれば、それ以降コンピューターは不要になります。また、入力材料として[インクカートリッジ](../item/inkCartridge.md)と[カメリウム](../item/chamelium.md)を用意する必要があります。使用されるカメリウムの量は3Dプリントの体積に依存し、インクの量はプリントされるアイテムの表面積に依存します。

アイテムをプリントするには、以下のコマンドを使用します：
`print3d /path/to/file.3dm`
（.3dm ファイルへのパスを指定します）

独自のモデルを作成するためのドキュメントは `/usr/share/models/example.3dm` にあります。
